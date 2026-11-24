package dev.kintsugi.assurance;

import java.util.Objects;

/** Reduces qualified evidence for one intended JVM and one observation window. */
public final class Assessment {
    public enum HostEvidence { VERIFIED_PRESENT, VERIFIED_RETIRED, UNVERIFIED }
    public enum Behavior { GUARD_OBSERVED, BASELINE_OBSERVED, INCONCLUSIVE }
    public enum Conclusion {
        BEHAVIOR_CORROBORATED, COVERAGE_GAP, RESIDUAL_EFFECT, RETIREMENT_CORROBORATED, UNKNOWN
    }

    private Assessment() {
    }

    public static Conclusion assess(HostEvidence host, Behavior behavior) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(behavior, "behavior");
        return switch (host) {
            case UNVERIFIED -> Conclusion.UNKNOWN;
            case VERIFIED_PRESENT -> switch (behavior) {
                case GUARD_OBSERVED -> Conclusion.BEHAVIOR_CORROBORATED;
                case BASELINE_OBSERVED -> Conclusion.COVERAGE_GAP;
                case INCONCLUSIVE -> Conclusion.UNKNOWN;
            };
            case VERIFIED_RETIRED -> switch (behavior) {
                case GUARD_OBSERVED -> Conclusion.RESIDUAL_EFFECT;
                case BASELINE_OBSERVED -> Conclusion.RETIREMENT_CORROBORATED;
                case INCONCLUSIVE -> Conclusion.UNKNOWN;
            };
        };
    }
}
