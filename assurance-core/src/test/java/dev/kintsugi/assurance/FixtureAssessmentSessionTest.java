package dev.kintsugi.assurance;

import dev.kintsugi.assurance.CoverageAssessment.*;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic protocol tests only; these do not represent successful Shirudo verification. */
class FixtureAssessmentSessionTest {
    final Instant now = Instant.parse("2026-01-01T00:00:01Z");
    final Duration ttl = Duration.ofSeconds(10);
    final Identity pid = new Identity("boot", "pid:[1]", "mnt:[1]", 42, 100, "/java", 1, 2, "1000");
    final Shield shield = new Shield("sql", "a".repeat(64));
    final Intent intent = new Intent(pid, shield);
    final Host host = new Host(shield, HostState.VERIFIED_PRESENT, now);
    final String identity = "label=test\tinstance=00000000-0000-4000-8000-000000000001\tpid=42"
            + "\tstarted=2026-01-01T00:00:00Z\tboot=boot\tpid_namespace=pid:[1]\tstart_ticks=100";
    FixtureAssessmentSession session() {
        return new FixtureAssessmentSession(true, intent, host, pid, "event=ready\t" + identity, now, ttl);
    }
    String response(FixtureAssessmentSession s) {
        return "event=check\t" + identity + "\tnonce=" + s.command().substring(6)
                + "\tat=" + now.plusSeconds(1) + "\tcontrol=scalar\tafter_control=scalar\thealth=ok"
                + "\tdiscriminator=scalar\tbehavior=baseline_observed";
    }
    @Test void bindsBaselineAndConsumesChallenge() {
        var s = session();
        var b = s.accept(response(s), pid, pid, now.plusSeconds(2));
        assertTrue(b.qualified());
        assertEquals(shield, b.shield());
        assertEquals(Assessment.Behavior.BASELINE_OBSERVED, b.outcome());
        assertEquals(Assessment.Conclusion.COVERAGE_GAP, CoverageAssessment.assess(intent, host,
                new Startup(pid, Activation.OPTION_ABSENT, now), b, pid, now.plusSeconds(2), ttl).conclusion());
        assertFalse(s.accept(response(s), pid, pid, now.plusSeconds(2)).qualified());
        assertNotEquals(s.command(), session().command());
    }
    @Test void validatesGuardProtocolWithoutClaimingRealProtection() {
        var s = session();
        var b = s.accept(response(s).replace("discriminator=scalar", "discriminator=guard_exception")
                .replace("behavior=baseline_observed", "behavior=guard_observed"), pid, pid, now.plusSeconds(2));
        assertTrue(b.qualified());
        assertEquals(Assessment.Behavior.GUARD_OBSERVED, b.outcome());
    }
    @ParameterizedTest
    @ValueSource(strings={"nonce", "pid", "instance", "health", "control", "duplicate", "missing", "future", "conflict"})
    void rejectsInvalidBindings(String fault) {
        var s = session();
        String line = response(s);
        line = switch (fault) {
            case "nonce" -> line.replace(s.command().substring(6), "00000000-0000-4000-8000-000000000002");
            case "pid" -> line.replace("pid=42", "pid=43");
            case "instance" -> line.replace("000000000001", "000000000002");
            case "health" -> line.replace("health=ok", "health=failed");
            case "control" -> line.replace("after_control=scalar", "after_control=error");
            case "duplicate" -> line + "\thealth=ok";
            case "missing" -> null;
            case "future" -> line.replace(now.plusSeconds(1).toString(), now.plusSeconds(3).toString());
            default -> line.replace("behavior=baseline_observed", "behavior=guard_observed");
        };
        var b = s.accept(line, pid, pid, now.plusSeconds(2));
        assertFalse(b.qualified());
        assertEquals(Assessment.Conclusion.UNKNOWN, CoverageAssessment.assess(intent, host,
                new Startup(pid, Activation.EXPECTED_OPTION, now), b, pid, now.plusSeconds(2), ttl).conclusion());
        assertFalse(s.accept(response(s), pid, pid, now.plusSeconds(2)).qualified());
    }
    @Test void rejectsStaleOrChangedProcessAndUnauthorizedHost() {
        var s = session();
        assertFalse(s.accept(response(s), pid, pid, now.plusSeconds(11)).qualified());
        s = session();
        assertFalse(s.accept(response(s), pid, null, now.plusSeconds(2)).qualified());
        assertThrows(IllegalArgumentException.class, () -> new FixtureAssessmentSession(false, intent, host, pid,
                "event=ready\t" + identity, now, ttl));
        assertThrows(IllegalArgumentException.class, () -> new FixtureAssessmentSession(true, intent,
                new Host(shield, HostState.UNKNOWN, now), pid, "event=ready\t" + identity, now, ttl));
        assertThrows(IllegalArgumentException.class, () -> new FixtureAssessmentSession(true, intent,
                new Host(new Shield("sql", "b".repeat(64)), HostState.VERIFIED_PRESENT, now), pid,
                "event=ready\t" + identity, now, ttl));
    }
}
