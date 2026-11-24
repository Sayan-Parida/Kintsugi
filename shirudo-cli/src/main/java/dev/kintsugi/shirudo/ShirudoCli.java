package dev.kintsugi.shirudo;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only adapter. Target collection uses the authenticated runtime executable.
 * Verification must run separately in the trusted operator domain.
 */
public final class ShirudoCli {
    public enum State { VERIFIED, FAILED, UNKNOWN }
    public record Evidence(State state, String reason, String rawOutput, String stderr,
                           JsonNode document, Instant collectedAt, List<String> command) {
        public Evidence {
            command = List.copyOf(command);
        }
    }
    public record HostCollection(Evidence status, Evidence shields, Evidence attestation) {
    }

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private final Path executable;
    private final Duration timeout;

    public ShirudoCli(Path executable, Duration timeout) {
        if (!executable.isAbsolute() || timeout.isNegative() || timeout.isZero()
                || timeout.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("Use an absolute executable and timeout in (0, 5 minutes]");
        }
        this.executable = executable.normalize();
        this.timeout = timeout;
    }

    public HostCollection collect(String nonce) {
        validateNonce(nonce);
        return new HostCollection(status(), shields(), attest(nonce));
    }

    public Evidence status() {
        return invoke(List.of("status", "--json"), "status", null);
    }

    public Evidence shields() {
        return invoke(List.of("upatch", "list", "--json"), "shields", null);
    }

    /** Returns the native evidence on stdout without writing privileged target paths. */
    public Evidence attest(String nonce) {
        validateNonce(nonce);
        return invoke(List.of("attest", "--nonce=" + nonce), "attestation", nonce);
    }

    /** Evidence is an operator-owned local file containing the original collected bytes. */
    public Evidence verify(Path evidence, String nonce, VerificationPolicy policy) {
        validateNonce(nonce);
        java.util.Objects.requireNonNull(policy);
        if (!evidence.isAbsolute() || !Files.isRegularFile(evidence) || !Files.isReadable(evidence)) {
            return result(State.UNKNOWN, "evidence_file_unavailable", "", "", null, List.of());
        }
        var args = new ArrayList<>(List.of("attest", "verify", "--evidence=" + evidence.normalize(),
                "--nonce=" + nonce, "--ak-name=" + policy.akName(),
                "--expect-chain=" + policy.expectedChain(), "--expect-bind-pcr=9=" + policy.bindPcr9(),
                "--expect-bind-pcr=11=" + policy.bindPcr11(),
                "--expect-config-root=" + policy.configRoot(), "--expect-config-pcr=" + policy.configPcr(),
                "--json"));
        policy.bootPcrs().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> args.add("--expect-pcr=" + entry.getKey() + "=" + entry.getValue()));
        return invoke(args, "verification", nonce);
    }

    private Evidence invoke(List<String> args, String kind, String nonce) {
        var command = new ArrayList<String>();
        command.add(executable.toString());
        command.addAll(args);
        if (!System.getProperty("os.name").equalsIgnoreCase("Linux")) {
            return result(State.UNKNOWN, "unsupported_environment", "", "", null, command);
        }
        return interpret(CliProcess.run(command, timeout), kind, nonce, command);
    }

    static Evidence interpret(CliProcess.Result output, String kind, String nonce, List<String> command) {
        if (output.failure() != CliProcess.Failure.NONE) {
            return result(State.UNKNOWN, output.failure().name().toLowerCase(java.util.Locale.ROOT),
                    output.stdout(), output.stderr(), null, command);
        }
        if (output.exitCode() != 0 && !kind.equals("verification")) {
            String diagnostic = output.stderr().toLowerCase(java.util.Locale.ROOT);
            String reason = diagnostic.contains("permission denied") || diagnostic.contains("operation not permitted")
                    ? "permission_denied" : "command_exit_" + output.exitCode();
            return result(State.UNKNOWN, reason, output.stdout(), output.stderr(), null, command);
        }
        JsonNode doc;
        try {
            doc = JSON.readTree(output.stdout());
            if (doc == null || !doc.isObject() || !doc.path("schema").isIntegralNumber()
                    || !doc.path("schema").canConvertToInt() || doc.path("schema").intValue() != 1) {
                throw new IOException("Unsupported schema");
            }
        } catch (IOException invalid) {
            return result(State.UNKNOWN, "malformed_or_unsupported_output", output.stdout(),
                    output.stderr(), null, command);
        }
        State state = State.UNKNOWN;
        String reason = "local_observation";
        switch (kind) {
            case "verification" -> {
                String verdict = doc.path("verdict").asText();
                JsonNode checks = doc.path("checks");
                if ((!verdict.equals("ok") && !verdict.equals("fail")) || !checks.isArray() || checks.isEmpty()) {
                    reason = "malformed_verification_report";
                } else if (verdict.equals("fail") && output.exitCode() == 1) {
                    state = State.FAILED;
                    reason = "native_verification_failed";
                } else if (verdict.equals("ok") && output.exitCode() == 0
                        && validChecks(checks)) {
                    state = State.VERIFIED;
                    reason = "native_verification_passed_for_supplied_policy";
                } else {
                    reason = "inconsistent_verification_report";
                }
            }
            case "status" -> {
                String verdict = doc.path("status").asText();
                if (!List.of("ok", "tampered", "lsm-off", "init-pending", "reboot-pending", "degraded")
                        .contains(verdict) || !doc.path("conditions").isArray()) {
                    reason = "malformed_status";
                } else if (output.exitCode() == 0 && verdict.equals("tampered")) {
                    state = State.FAILED;
                    reason = "reported_tampered";
                } else if (output.exitCode() == 0) {
                    reason = "reported_" + verdict;
                }
            }
            case "shields" -> {
                if (!doc.path("upatches").isArray()) {
                    reason = "malformed_shield_list";
                }
            }
            case "attestation" -> {
                reason = doc.path("kind").asText().equals("shirudo-attestation")
                        && doc.path("nonce").asText().equalsIgnoreCase(nonce)
                        && doc.path("quote").isObject() && doc.path("register").isObject()
                        ? "unverified_attestation" : "malformed_attestation";
            }
            default -> throw new IllegalArgumentException("Unsupported response kind");
        }
        return result(state, reason, output.stdout(), output.stderr(), doc, command);
    }

    private static boolean validChecks(JsonNode checks) {
        boolean hasOk = false;
        for (JsonNode check : checks) {
            String value = check.path("result").asText();
            if (!List.of("ok", "note").contains(value) || !check.path("text").isTextual()) {
                return false;
            }
            hasOk |= value.equals("ok");
        }
        return hasOk;
    }

    private static Evidence result(State state, String reason, String raw, String stderr,
                                   JsonNode doc, List<String> command) {
        return new Evidence(state, reason, raw, stderr, doc, Instant.now(), command);
    }

    private static void validateNonce(String nonce) {
        if (nonce == null || !nonce.matches("(?:[0-9a-fA-F]{2}){1,64}")) {
            throw new IllegalArgumentException("Nonce must be 1 to 64 hexadecimal bytes");
        }
    }
}
