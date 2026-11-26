package dev.kintsugi.orchestrator;

import dev.kintsugi.assurance.CoverageAssessment.*;
import dev.kintsugi.assurance.FixtureAssessmentSession;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FixturePipesTest {
    final String banner = "[RASP Agent] Initializing SQL Injection Shield...";
    final String ready = "event=ready\tlabel=B\tinstance=00000000-0000-4000-8000-000000000001"
            + "\tpid=42\tstarted=2026-01-01T00:00:00Z\tboot=boot\tpid_namespace=pid:[1]\tstart_ticks=100";
    InputStream input(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test void plainReadyRemainsUnchangedForEitherStartupState() throws Exception {
        assertEquals(ready, FixturePipes.readyRecord(input(ready + "\n"), false));
        assertEquals(ready, FixturePipes.readyRecord(input(ready + "\n"), true));
    }

    @Test void permitsExactlyOneKnownBannerOnlyForExpectedSqlAgent() throws Exception {
        assertEquals(ready, FixturePipes.readyRecord(input(banner + "\n" + ready + "\n"), true));
        assertThrows(IOException.class, () -> FixturePipes.readyRecord(input(banner + "\n" + ready + "\n"), false));
        assertThrows(IOException.class, () -> FixturePipes.readyRecord(input(banner + "\n" + banner + "\n" + ready + "\n"), true));
    }

    @Test void rejectsUnknownAndAlteredPreambles() {
        for (String preamble : new String[]{"noise", banner + " ", "event=check", banner.toLowerCase()}) {
            assertThrows(IOException.class, () -> FixturePipes.readyRecord(input(preamble + "\n" + ready + "\n"), true));
        }
    }

    @Test void rejectsUnterminatedMissingAndOversizedReady() {
        for (String text : new String[]{banner + "\n", banner + "\n" + ready,
                banner + "\n" + "x".repeat(8192) + "\n", "x".repeat(8192) + "\n" + ready + "\n"}) {
            assertThrows(IOException.class, () -> FixturePipes.readyRecord(input(text), true));
        }
    }

    @Test void bannerHandlingDoesNotQualifyWrongProcessIdentity() throws Exception {
        var now = Instant.parse("2026-01-01T00:00:01Z");
        var pid = new Identity("boot", "pid:[1]", "mnt:[1]", 43, 100, "/java", 1, 2, "1000");
        var shield = new Shield("java-sql-inj", "a".repeat(64));
        String line = FixturePipes.readyRecord(input(banner + "\n" + ready + "\n"), true);
        assertThrows(IllegalArgumentException.class, () -> new FixtureAssessmentSession(true,
                new Intent(pid, shield), new Host(shield, HostState.VERIFIED_PRESENT, now),
                pid, line, now, Duration.ofSeconds(10)));
    }

    @Test void leavesFollowingResponseBytesForStrictSessionValidation() throws Exception {
        var stream = input(banner + "\n" + ready + "\n" + "untrusted-response\n");
        assertEquals(ready, FixturePipes.readyRecord(stream, true));
        assertEquals("untrusted-response\n", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    }
}
