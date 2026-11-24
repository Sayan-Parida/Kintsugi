package dev.kintsugi.linux;

import dev.kintsugi.assurance.CoverageAssessment.Activation;
import dev.kintsugi.assurance.CoverageAssessment.Identity;
import dev.kintsugi.assurance.CoverageAssessment.Startup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;

/** Passive inspection in the observer's procfs view. Raw arguments and environment are discarded. */
public final class JvmStartupInspector {
    private final Path proc;

    public JvmStartupInspector(Path proc) {
        this.proc = proc;
    }

    public Identity identity(long pid) throws IOException {
        Identity first = readIdentity(pid);
        if (!first.equals(readIdentity(pid))) throw new IOException("Process identity changed during read");
        return first;
    }

    private Identity readIdentity(long pid) throws IOException {
        Path base = proc.resolve(Long.toString(pid));
        String stat = read(base.resolve("stat"));
        String uid = read(base.resolve("status")).lines().filter(line -> line.startsWith("Uid:"))
                .findFirst().orElseThrow(() -> new IOException("UID unavailable")).substring(4).strip();
        return new Identity(read(proc.resolve("sys/kernel/random/boot_id")).strip(),
                Files.readSymbolicLink(base.resolve("ns/pid")).toString(),
                Files.readSymbolicLink(base.resolve("ns/mnt")).toString(), pid,
                JvmInventory.startTicks(stat), Files.readSymbolicLink(base.resolve("exe")).toString(),
                ((Number) Files.getAttribute(base.resolve("exe"), "unix:dev")).longValue(),
                ((Number) Files.getAttribute(base.resolve("exe"), "unix:ino")).longValue(), uid);
    }

    public Startup inspect(JvmInventory.Snapshot inventory, JvmInventory.Candidate candidate, Path expectedAgent) {
        if (!expectedAgent.isAbsolute()) throw new IllegalArgumentException("Agent path must be absolute");
        Identity before = null;
        try {
            before = identity(candidate.pid());
            if (!before.bootId().equals(inventory.bootId()) || before.startTicks() != candidate.startTicks()) {
                return new Startup(null, Activation.UNKNOWN, Instant.now());
            }
            Path base = proc.resolve(Long.toString(candidate.pid()));
            String command = read(base.resolve("cmdline"));
            String environment = read(base.resolve("environ"));
            if (!before.equals(identity(candidate.pid()))) {
                return new Startup(null, Activation.UNKNOWN, Instant.now());
            }
            return new Startup(before, classify(command, environment, expectedAgent), Instant.now());
        } catch (IOException | IllegalArgumentException | UnsupportedOperationException inaccessible) {
            return new Startup(before, Activation.INACCESSIBLE, Instant.now());
        }
    }

    static Activation classify(String command, String environment, Path expectedAgent) {
        var options = new ArrayList<String>();
        // Arguments after the launcher main/JAR may be application data. Treat matches as clues only.
        for (String arg : command.split("\u0000")) options.add(arg);
        boolean uncertain = false;
        for (String variable : environment.split("\u0000")) {
            for (String key : java.util.List.of("JAVA_TOOL_OPTIONS=", "JDK_JAVA_OPTIONS=", "_JAVA_OPTIONS=")) {
                if (variable.startsWith(key)) {
                    String value = variable.substring(key.length());
                    if (value.contains("'") || value.contains("\"")) uncertain = true;
                    options.addAll(java.util.List.of(value.split("\\s+")));
                }
            }
        }
        if (uncertain) return Activation.UNKNOWN;
        long matches = options.stream().filter(option -> option.equals("-javaagent:" + expectedAgent)
                || option.startsWith("-javaagent:" + expectedAgent + "=")).count();
        boolean otherAgents = options.stream().anyMatch(option -> option.startsWith("-javaagent:")
                && !option.equals("-javaagent:" + expectedAgent)
                && !option.startsWith("-javaagent:" + expectedAgent + "="));
        Activation activation = matches > 1 || otherAgents ? Activation.CONFLICTING_OPTIONS
                : matches == 1 ? Activation.EXPECTED_OPTION : Activation.OPTION_ABSENT;
        return activation;
    }

    private static String read(Path file) throws IOException {
        try (var input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(1024 * 1024 + 1);
            if (bytes.length > 1024 * 1024) throw new IOException("Procfs read limit");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
