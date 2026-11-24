package dev.kintsugi.assurance;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssessmentTest {
    @ParameterizedTest
    @CsvSource({
        "VERIFIED_PRESENT, GUARD_OBSERVED, BEHAVIOR_CORROBORATED",
        "VERIFIED_PRESENT, BASELINE_OBSERVED, COVERAGE_GAP",
        "VERIFIED_PRESENT, INCONCLUSIVE, UNKNOWN",
        "VERIFIED_RETIRED, GUARD_OBSERVED, RESIDUAL_EFFECT",
        "VERIFIED_RETIRED, BASELINE_OBSERVED, RETIREMENT_CORROBORATED",
        "VERIFIED_RETIRED, INCONCLUSIVE, UNKNOWN",
        "UNVERIFIED, GUARD_OBSERVED, UNKNOWN",
        "UNVERIFIED, BASELINE_OBSERVED, UNKNOWN",
        "UNVERIFIED, INCONCLUSIVE, UNKNOWN"
    })
    void assessesQualifiedEvidence(Assessment.HostEvidence host, Assessment.Behavior behavior,
                                   Assessment.Conclusion expected) {
        assertEquals(expected, Assessment.assess(host, behavior));
    }
}
