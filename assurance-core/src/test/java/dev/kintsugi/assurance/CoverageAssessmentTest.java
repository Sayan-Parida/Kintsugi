package dev.kintsugi.assurance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.Duration;
import java.time.Instant;
import static dev.kintsugi.assurance.CoverageAssessment.*;
import static org.junit.jupiter.api.Assertions.*;

class CoverageAssessmentTest {
    final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    final Duration age = Duration.ofSeconds(30);
    final Identity identity = new Identity("boot", "pidns", "mountns", 12, 99, "/jdk/bin/java", 1, 100, "1000");
    final Shield shield = new Shield("java-sql-inj", "a".repeat(64));
    final Intent intent = new Intent(identity, shield);
    final Host host = new Host(shield, HostState.VERIFIED_PRESENT, now);
    final Startup startup = new Startup(identity, Activation.EXPECTED_OPTION, now);
    final Behavior behavior = new Behavior(identity, shield, Assessment.Behavior.GUARD_OBSERVED, true, now);

    Result assess(Host h, Startup s, Behavior b, Identity current) {
        return CoverageAssessment.assess(intent, h, s, b, current, now, age);
    }

    @Test void positiveRequiresAllEvidenceAndSameLifetime() {
        assertEquals(Assessment.Conclusion.BEHAVIOR_CORROBORATED,
                assess(host, startup, behavior, identity).conclusion());
        assertEquals(Assessment.Conclusion.UNKNOWN, assess(host, startup, null, identity).conclusion());
        assertEquals(Assessment.Conclusion.UNKNOWN, assess(host, null, behavior, identity).conclusion());
        assertEquals(Assessment.Conclusion.UNKNOWN, assess(null, startup, behavior, identity).conclusion());
    }

    @Test void baselineCanDemonstrateGapDespiteStartupOption() {
        var baseline = new Behavior(identity, shield, Assessment.Behavior.BASELINE_OBSERVED, true, now);
        assertEquals(Assessment.Conclusion.COVERAGE_GAP, assess(host, startup, baseline, identity).conclusion());
        assertEquals(Assessment.Conclusion.RESIDUAL_EFFECT,
                assess(new Host(shield, HostState.VERIFIED_RETIRED, now), startup, behavior, identity).conclusion());
    }

    @ParameterizedTest @EnumSource(value=Activation.class,
            names={"CONFLICTING_OPTIONS","INACCESSIBLE","UNKNOWN"})
    void ambiguousStartupRetainsUnknown(Activation activation) {
        assertEquals(Assessment.Conclusion.UNKNOWN,
                assess(host, new Startup(identity, activation, now), behavior, identity).conclusion());
    }

    @Test void pidReuseExitAndNamespaceChangesInvalidateEvidence() {
        assertEquals(Assessment.Conclusion.UNKNOWN, assess(host, startup, behavior, null).conclusion());
        var reused = new Identity("boot", "pidns", "mountns", 12, 100, "/jdk/bin/java", 1, 100, "1000");
        assertEquals(Assessment.Conclusion.UNKNOWN, assess(host, startup, behavior, reused).conclusion());
        var moved = new Identity("boot", "otherns", "mountns", 12, 99, "/jdk/bin/java", 1, 100, "1000");
        assertEquals(Assessment.Conclusion.UNKNOWN, assess(host, startup, behavior, moved).conclusion());
        assertEquals(Assessment.Conclusion.UNKNOWN,
                assess(host, startup, new Behavior(reused, shield, behavior.outcome(), true, now), identity).conclusion());
    }

    @Test void staleFutureConflictingAndUnqualifiedEvidenceRemainUnknown() {
        assertEquals(Assessment.Conclusion.UNKNOWN,
                assess(new Host(shield, host.state(), now.minusSeconds(31)), startup, behavior, identity).conclusion());
        assertEquals(Assessment.Conclusion.UNKNOWN,
                assess(host, new Startup(identity, Activation.EXPECTED_OPTION, now.plusSeconds(1)), behavior, identity).conclusion());
        assertEquals(Assessment.Conclusion.UNKNOWN,
                assess(host, startup, new Behavior(identity, shield, behavior.outcome(), false, now), identity).conclusion());
        assertEquals(Assessment.Conclusion.UNKNOWN,
                assess(new Host(new Shield("other", "b".repeat(64)), host.state(), now), startup, behavior, identity).conclusion());
        assertEquals(Assessment.Conclusion.UNKNOWN,
                assess(new Host(shield, HostState.FAILED, now), startup, behavior, identity).conclusion());
    }
}
