package dev.kintsugi.orchestrator;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import dev.kintsugi.assurance.CoverageAssessment.*;
import dev.kintsugi.linux.*;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

final class AssessmentCli {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
    private static final String HELP = """
            Kintsugi | read-only JVM shield assurance
              inventory [--json]
              verify-host --profile=FILE [--json]
              assess --pid=N --shield=NAME --digest=SHA256 [--profile=FILE] [--fixture-pipes] [--json]
            --fixture-pipes authorizes the named test JVM only. Inherit its stdout on stdin and its stdin on FD 3.
            Exit 0 completed, 1 verification failed, 2 invalid configuration, 3 unknown or incomplete.
            """;
    private final Path proc;
    private final boolean linux;

    AssessmentCli(Path proc, boolean linux) { this.proc = proc; this.linux = linux; }

    int run(String[] args, PrintStream out) {
        boolean json = Arrays.asList(args).contains("--json");
        if (args.length == 0 || Arrays.asList(args).contains("--help")) { out.print(HELP); return 0; }
        var report = new LinkedHashMap<String,Object>();
        int exit;
        try {
            String command = args[0];
            Set<String> allowed = switch (command) {
                case "inventory" -> Set.of("json");
                case "verify-host" -> Set.of("json", "profile");
                case "assess" -> Set.of("json", "pid", "shield", "digest", "profile", "fixture-pipes");
                default -> throw new IllegalArgumentException();
            };
            var options = new HashMap<String,String>();
            for (int i = 1; i < args.length; i++) {
                if (!args[i].startsWith("--")) throw new IllegalArgumentException();
                String[] part = args[i].substring(2).split("=", 2);
                boolean flag = Set.of("json","fixture-pipes").contains(part[0]);
                if (!allowed.contains(part[0]) || flag != (part.length == 1)
                        || (!flag && part[1].isBlank())
                        || options.put(part[0], flag ? "true" : part[1]) != null) throw new IllegalArgumentException();
            }
            OperatorProfile profile = null;
            if (options.containsKey("profile")) {
                try { profile = OperatorProfile.load(Path.of(options.get("profile")), JSON); }
                catch (IOException invalid) { throw new IllegalArgumentException(); }
            }
            if ((command.equals("verify-host") || options.containsKey("fixture-pipes")) && profile == null)
                throw new IllegalArgumentException();
            report.put("command", command);
            if (command.equals("assess")) {
                long pid = Long.parseLong(options.get("pid"));
                if (pid <= 0) throw new IllegalArgumentException();
                var shield = new Shield(options.get("shield"), options.get("digest"));
                exit = assess(pid, shield, profile, options.containsKey("fixture-pipes"), report);
            } else if (command.equals("verify-host")) {
                var receipt = receipt(profile, null, report);
                exit = receipt == null ? 3 : switch (receipt.verification().state()) {
                    case VERIFIED -> 0; case FAILED -> 1; case UNKNOWN -> 3;
                };
            } else {
                if (!linux) throw new IOException();
                var snapshot = new JvmInventory(proc).scan(Duration.ofSeconds(5));
                report.put("bootId", snapshot.bootId());
                report.put("unreadable", snapshot.unreadable());
                report.put("truncated", snapshot.truncated());
                report.put("jvms", snapshot.candidates().stream().map(c -> Map.of(
                        "pid", c.pid(), "startTicks", c.startTicks(), "coverage", "UNKNOWN")).toList());
                exit = snapshot.truncated() ? 3 : 0;
            }
        } catch (IOException | SecurityException unavailable) {
            report.put("reasons", List.of(linux ? "procfs_unavailable" : "unsupported_environment"));
            exit = 3;
        } catch (RuntimeException invalid) {
            report.clear();
            report.put("error", "invalid_configuration");
            exit = 2;
        }
        try {
            if (json) out.println(JSON.writeValueAsString(report));
            else report.forEach((key,value) -> {
                if (key.equals("jvms") && value instanceof List<?> rows) {
                    out.printf("Visible JVMs (%d)%n", rows.size());
                    for (var row : rows) out.println("  " + row);
                } else out.printf("%s = %s%n", key, value);
            });
        } catch (IOException failure) { return 3; }
        return exit;
    }

    private OperatorProfile.Receipt receipt(OperatorProfile p, String boot, Map<String,Object> report) {
        if (p == null || !p.current()) {
            report.put("hostVerification", "UNKNOWN");
            report.put("hostReason", p == null ? "profile_missing" : "challenge_window_invalid");
            return null;
        }
        var r = p.verify(boot);
        if (!p.current()) {
            report.put("hostVerification", "UNKNOWN");
            report.put("hostReason", "challenge_window_expired");
            return null;
        }
        report.put("hostVerification", r.verification().state());
        report.put("hostReason", r.verification().reason());
        return r;
    }

    private int assess(long pid, Shield shield, OperatorProfile profile, boolean fixture, Map<String,Object> report)
            throws IOException {
        report.put("pid", pid);
        report.put("shield", shield.name());
        report.put("digest", shield.payloadDigest());
        report.put("hostVerification", "UNKNOWN");
        report.put("startup", "UNKNOWN");
        report.put("behaviour", "INCONCLUSIVE");
        report.put("probeAssessment", "UNKNOWN");
        report.put("coverage", "UNKNOWN");
        if (!linux) throw new IOException();
        var inventory = new JvmInventory(proc).scan(Duration.ofSeconds(5));
        var r = receipt(profile, inventory.bootId(), report);
        var candidate = inventory.candidates().stream().filter(c -> c.pid() == pid).findFirst();
        if (candidate.isEmpty()) {
            report.put("reasons", List.of(inventory.truncated() ? "inventory_truncated" : "jvm_not_visible"));
            return 3;
        }
        var inspector = new JvmStartupInspector(proc);
        var service = new CoverageService(inspector);
        Identity identity;
        try { identity = inspector.identity(pid); }
        catch (IOException | IllegalArgumentException | UnsupportedOperationException unavailable) {
            report.put("reasons", List.of("process_ended_or_identity_inaccessible"));
            return 3;
        }
        var intent = new Intent(identity, shield);
        var agent = Path.of("/run/shirudo-java", shield.name() + ".jar");
        var c = candidate.get();
        var result = service.assess(intent, inventory, c, agent, r == null ? null : r.approved(),
                r == null ? null : r.chain(), r == null ? null : r.verification(), null, Duration.ofMinutes(1));
        String detail = null;
        if (fixture && r != null && result.host() != null && result.host().shield().equals(shield)
                && (result.host().state() == HostState.VERIFIED_PRESENT || result.host().state() == HostState.VERIFIED_RETIRED)
                && intent.process().equals(result.startup().process())) {
            try {
                result = FixturePipes.assess(proc, service, intent, inventory, c, agent, r);
                if (result.behavior().qualified()) detail = "closing_host_attestation_missing";
            } catch (java.util.concurrent.TimeoutException timeout) { detail = "fixture_timeout"; }
            catch (Exception unavailable) { detail = "fixture_channel_or_session_invalid"; }
        }
        var reasons = new ArrayList<>(result.reasons());
        if (detail != null) reasons.add(detail);
        if (profile != null && profile.mapping() != null && !inventory.bootId().equals(profile.mapping().bootId()))
            reasons.add("approved_boot_id_mismatch");
        if (r != null && !profile.current()) reasons.add("challenge_window_expired");
        else report.put("probeAssessment", result.conclusion());
        report.put("hostShieldState", result.host() == null ? HostState.UNKNOWN : result.host().state());
        report.put("startup", result.startup().activation());
        if (result.behavior() != null) report.put("behaviour", result.behavior().outcome());
        report.put("reasons", reasons);
        // A single pre-probe host receipt cannot establish a stable measurement interval.
        return r != null && r.verification().state() == dev.kintsugi.shirudo.ShirudoCli.State.FAILED ? 1 : 3;
    }
}
