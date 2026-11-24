package dev.kintsugi.fixtures;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Cooperative test process. Output is tab-separated key=value records.
 * All SQL is fixed, scalar and read-only.
 */
public final class SqlShieldFixture {
    private static final String GUARD_MESSAGE = "Request blocked by RASP: Malicious SQL patterns detected.";
    private static final String CONTROL = "kintsugi-control";
    private static final String DISCRIMINATOR = "OR 2=2";

    private SqlShieldFixture() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !args[0].matches("[A-Za-z0-9_-]{1,40}")) {
            throw new IllegalArgumentException("Supply one instance label (letters, digits, underscore or dash)");
        }
        ProcessHandle process = ProcessHandle.current();
        String identity = "label=" + args[0] + "\tinstance=" + UUID.randomUUID()
                + "\tpid=" + process.pid()
                + "\tstarted=" + process.info().startInstant().map(Instant::toString).orElse("unavailable")
                + "\tboot=" + linuxValue("sys/kernel/random/boot_id")
                + "\tpid_namespace=" + linuxNamespace()
                + "\tstart_ticks=" + linuxStartTicks();

        // Explicit driver load also supports JVMs which already created JDBC infrastructure.
        Class.forName("org.h2.Driver");
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:kintsugi");
             Statement statement = connection.createStatement();
             BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            PrintWriter output = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
            Set<UUID> used = new HashSet<>();
            output.println("event=ready\t" + identity);
            for (String line; (line = input.readLine()) != null;) {
                if (line.equals("QUIT")) {
                    output.println("event=stopped\t" + identity);
                    return;
                }
                if (!line.startsWith("CHECK ") || line.length() != 42) {
                    output.println("event=error\treason=expected_CHECK_UUID");
                    continue;
                }
                UUID nonce;
                try {
                    nonce = UUID.fromString(line.substring(6));
                    if (!nonce.toString().equals(line.substring(6))) {
                        throw new IllegalArgumentException();
                    }
                } catch (IllegalArgumentException invalid) {
                    output.println("event=error\treason=invalid_nonce");
                    continue;
                }
                if (used.size() >= 10000 || !used.add(nonce)) {
                    output.println("event=error\treason=nonce_reused_or_limit");
                    continue;
                }

                String control = query(statement, "SELECT 'kintsugi-control'", CONTROL);
                String discriminator = query(statement, "SELECT 'OR 2=2'", DISCRIMINATOR);
                String after = query(statement, "SELECT 'kintsugi-control'", CONTROL);
                boolean healthy = control.equals("scalar") && after.equals("scalar") && connection.isValid(1);
                String behavior = "inconclusive";
                if (healthy && discriminator.equals("guard_exception")) {
                    behavior = "guard_observed";
                } else if (healthy && discriminator.equals("scalar")) {
                    behavior = "baseline_observed";
                }
                output.println("event=check\t" + identity + "\tnonce=" + nonce
                        + "\tat=" + Instant.now() + "\tcontrol=" + control
                        + "\tdiscriminator=" + discriminator + "\tafter_control=" + after
                        + "\thealth=" + (healthy ? "ok" : "failed") + "\tbehavior=" + behavior);
            }
        }
    }

    private static String query(Statement statement, String sql, String expected) {
        try (ResultSet result = statement.executeQuery(sql)) {
            return result.next() && expected.equals(result.getString(1)) && !result.next()
                    ? "scalar" : "unexpected_scalar";
        } catch (SecurityException rejected) {
            return GUARD_MESSAGE.equals(rejected.getMessage()) ? "guard_exception" : "other_security_exception";
        } catch (Exception failed) {
            return "error_" + failed.getClass().getSimpleName();
        }
    }

    private static String linuxValue(String relative) {
        try {
            return Files.readString(Path.of("/proc").resolve(relative)).strip();
        } catch (Exception unavailable) {
            return "unavailable";
        }
    }

    private static String linuxNamespace() {
        try {
            return Files.readSymbolicLink(Path.of("/proc/self/ns/pid")).toString();
        } catch (Exception unavailable) {
            return "unavailable";
        }
    }

    private static String linuxStartTicks() {
        String stat = linuxValue("self/stat");
        int end = stat.lastIndexOf(')');
        if (end < 0) {
            return "unavailable";
        }
        String[] fields = stat.substring(end + 1).strip().split("\\s+");
        return fields.length >= 20 ? fields[19] : "unavailable";
    }
}
