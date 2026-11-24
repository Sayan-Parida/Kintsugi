package dev.kintsugi.fixtures;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class SqlShieldFixtureTest {
    @Test
    @Timeout(25)
    void independentProcessesRemainHealthyAcrossRepeatedChallenges() throws Exception {
        Process first = start("A");
        Process second = start("B");
        try {
            var a = new BufferedReader(new InputStreamReader(first.getInputStream(), StandardCharsets.UTF_8));
            var b = new BufferedReader(new InputStreamReader(second.getInputStream(), StandardCharsets.UTF_8));
            var ai = new PrintWriter(first.getOutputStream(), true, StandardCharsets.UTF_8);
            var bi = new PrintWriter(second.getOutputStream(), true, StandardCharsets.UTF_8);
            Map<String, String> ar = fields(a.readLine());
            Map<String, String> br = fields(b.readLine());
            assertEquals("ready", ar.get("event"));
            assertEquals("ready", br.get("event"));
            assertNotEquals(ar.get("instance"), br.get("instance"));
            assertEquals(Long.toString(first.pid()), ar.get("pid"));
            assertEquals(Long.toString(second.pid()), br.get("pid"));
            assertNotEquals(ar.get("pid"), br.get("pid"));

            UUID last = null;
            for (int i = 0; i < 3; i++) {
                last = UUID.randomUUID();
                ai.println("CHECK " + last);
                bi.println("CHECK " + last);
                Map<String, String> aReply = fields(a.readLine());
                Map<String, String> bReply = fields(b.readLine());
                for (String key : java.util.List.of("instance", "pid", "started", "boot", "pid_namespace", "start_ticks")) {
                    assertEquals(ar.get(key), aReply.get(key));
                    assertEquals(br.get(key), bReply.get(key));
                }
                for (Map<String, String> reply : java.util.List.of(aReply, bReply)) {
                    assertEquals(last.toString(), reply.get("nonce"));
                    assertEquals("ok", reply.get("health"));
                    assertEquals("scalar", reply.get("discriminator"));
                    assertEquals("baseline_observed", reply.get("behavior"));
                }
                assertTrue(first.isAlive());
                assertTrue(second.isAlive());
            }
            ai.println("CHECK " + last);
            assertEquals("nonce_reused_or_limit", fields(a.readLine()).get("reason"));
            ai.println("SELECT anything");
            assertEquals("expected_CHECK_UUID", fields(a.readLine()).get("reason"));
            ai.println("CHECK " + "x".repeat(36));
            assertEquals("invalid_nonce", fields(a.readLine()).get("reason"));
            ai.println("QUIT");
            bi.println("QUIT");
            assertEquals(ar.get("instance"), fields(a.readLine()).get("instance"));
            assertEquals(br.get("instance"), fields(b.readLine()).get("instance"));
            assertEquals(0, first.waitFor());
            assertEquals(0, second.waitFor());
        } finally {
            first.destroyForcibly();
            second.destroyForcibly();
        }
    }

    private Process start(String label) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-XX:-EnableDynamicAgentLoading", "-cp",
                System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                SqlShieldFixture.class.getName(), label);
        for (String key : java.util.List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")) {
            builder.environment().remove(key);
        }
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        return builder.start();
    }

    private Map<String, String> fields(String line) {
        assertNotNull(line, "Child ended before its response");
        return Stream.of(line.split("\t")).map(field -> field.split("=", 2))
                .collect(Collectors.toMap(pair -> pair[0], pair -> pair[1]));
    }
}
