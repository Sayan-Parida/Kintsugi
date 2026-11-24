package dev.kintsugi.shirudo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ShirudoCliTest {
    @TempDir Path directory;

    private String fixture(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/fixtures/" + name + ".json")) {
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private ShirudoCli.Evidence parse(String json, String kind, int exit) {
        return ShirudoCli.interpret(new CliProcess.Result(exit, json, "", CliProcess.Failure.NONE),
                kind, "aabb", List.of("SYNTHETIC_TEST_ONLY"));
    }

    @Test
    void syntheticReportsNeverPromoteCollectionToVerification() throws Exception {
        assertEquals(ShirudoCli.State.UNKNOWN, parse(fixture("status"), "status", 0).state());
        var shields = parse(fixture("shields"), "shields", 0);
        assertEquals(ShirudoCli.State.UNKNOWN, shields.state());
        assertEquals("java", shields.document().path("upatches").get(0).path("payload_type").asText());
        assertEquals("unverified_attestation", parse(fixture("attestation"), "attestation", 0).reason());
        assertEquals("malformed_attestation",
                parse(fixture("attestation").replace("aabb", "ccdd"), "attestation", 0).reason());
    }

    @Test
    void parsesSyntheticNativeVerifierVerdictsAndExitCodes() throws Exception {
        assertEquals(ShirudoCli.State.VERIFIED, parse(fixture("verify-ok"), "verification", 0).state());
        assertEquals(ShirudoCli.State.FAILED, parse(fixture("verify-fail"), "verification", 1).state());
        assertEquals(ShirudoCli.State.UNKNOWN, parse(fixture("verify-ok"), "verification", 1).state());
        assertEquals(ShirudoCli.State.UNKNOWN, parse(fixture("verify-fail"), "verification", 0).state());
        assertEquals(ShirudoCli.State.UNKNOWN, parse(fixture("verify-ok")
                .replace("\"result\":\"ok\"", "\"result\":\"fail\""), "verification", 0).state());
    }

    @ParameterizedTest
    @ValueSource(strings = {"oops", "{}", "{\"schema\":2}", "{\"schema\":1} {}",
            "{\"schema\":1,\"schema\":1}", "{\"schema\":1,\"verdict\":\"ok\",\"checks\":[]}"})
    void malformedOrUnsupportedVerifierOutputIsUnknown(String json) {
        assertEquals(ShirudoCli.State.UNKNOWN, parse(json, "verification", 0).state());
    }

    @Test
    void rejectsUnsafeInputsBeforeExecution() {
        assertThrows(IllegalArgumentException.class, () -> new ShirudoCli(Path.of("shirudo"), Duration.ofSeconds(1)));
        var cli = new ShirudoCli(directory.resolve("shirudo"), Duration.ofSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> cli.attest("aa; touch file"));
        assertThrows(IllegalArgumentException.class, () -> new VerificationPolicy("bad", "a".repeat(64),
                "a".repeat(64), "a".repeat(64), "a".repeat(64), 12, Map.of()));
        assertEquals(ShirudoCli.State.UNKNOWN, cli.verify(directory.resolve("missing"), "aabb", policy()).state());
    }

    @Test
    void permissionAndTamperReportsAreExplicit() {
        var denied = ShirudoCli.interpret(new CliProcess.Result(1, "{}", "Permission denied",
                CliProcess.Failure.NONE), "status", null, List.of());
        assertEquals(ShirudoCli.State.UNKNOWN, denied.state());
        assertEquals("permission_denied", denied.reason());
        assertEquals(ShirudoCli.State.FAILED,
                parse("{\"schema\":1,\"status\":\"tampered\",\"conditions\":[]}", "status", 0).state());
    }

    @Test
    void portableProcessRunnerHandlesPipesTimeoutAndMissingExecutable() {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> prefix = List.of(java, "-cp", System.getProperty("surefire.test.class.path",
                System.getProperty("java.class.path")), ControlledProcess.class.getName());
        var normal = new java.util.ArrayList<>(prefix);
        normal.add("normal");
        var result = CliProcess.run(normal, Duration.ofSeconds(10));
        assertEquals(CliProcess.Failure.NONE, result.failure());
        assertEquals("fixture-output", result.stdout());
        assertEquals("fixture-error", result.stderr());
        var slow = new java.util.ArrayList<>(prefix);
        slow.add("slow");
        assertEquals(CliProcess.Failure.TIMEOUT, CliProcess.run(slow, Duration.ofMillis(200)).failure());
        var large = new java.util.ArrayList<>(prefix);
        large.add("large");
        assertEquals(CliProcess.Failure.OUTPUT_LIMIT, CliProcess.run(large, Duration.ofSeconds(10)).failure());
        assertEquals(CliProcess.Failure.MISSING_EXECUTABLE,
                CliProcess.run(List.of(directory.resolve("missing").toString()), Duration.ofSeconds(1)).failure());
    }

    private VerificationPolicy policy() {
        return new VerificationPolicy("a".repeat(68), "b".repeat(64), "c".repeat(64),
                "d".repeat(64), "e".repeat(64), 12, Map.of(0, "f".repeat(64)));
    }

    @Test
    void buildsOnlyPinnedVerificationArgumentsAndReportsUnavailableRuntime() throws Exception {
        Path evidence = Files.writeString(directory.resolve("evidence with spaces.json"), "SYNTHETIC");
        var cli = new ShirudoCli(directory.resolve("missing-executable"), Duration.ofSeconds(1));
        var result = cli.verify(evidence, "aabb", policy());
        assertEquals(ShirudoCli.State.UNKNOWN, result.state());
        assertEquals(List.of(directory.resolve("missing-executable").toString(), "attest", "verify",
                "--evidence=" + evidence, "--nonce=aabb", "--ak-name=" + "a".repeat(68),
                "--expect-chain=" + "b".repeat(64), "--expect-bind-pcr=9=" + "c".repeat(64),
                "--expect-bind-pcr=11=" + "d".repeat(64), "--expect-config-root=" + "e".repeat(64),
                "--expect-config-pcr=12", "--json", "--expect-pcr=0=" + "f".repeat(64)), result.command());
        assertEquals(System.getProperty("os.name").equalsIgnoreCase("Linux")
                ? "missing_executable" : "unsupported_environment", result.reason());
    }

    public static class ControlledProcess {
        public static void main(String[] args) throws Exception {
            switch (args[0]) {
                case "slow" -> Thread.sleep(30000);
                case "large" -> System.out.print("x".repeat(9 * 1024 * 1024));
                default -> {
                    System.out.print("fixture-output");
                    System.err.print("fixture-error");
                }
            }
        }
    }
}
