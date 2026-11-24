package dev.kintsugi.assurance;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Correlates already-qualified evidence. Host claims must come from an approved operator profile. */
public final class CoverageAssessment {
    public record Identity(String bootId, String pidNamespace, String mountNamespace, long pid,
                           long startTicks, String executable, long executableDevice, long executableInode, String uid) {
        public Identity {
            Objects.requireNonNull(bootId);
            Objects.requireNonNull(pidNamespace);
            Objects.requireNonNull(mountNamespace);
            Objects.requireNonNull(executable);
            Objects.requireNonNull(uid);
            if (bootId.isBlank() || pidNamespace.isBlank() || mountNamespace.isBlank()
                    || executable.isBlank() || uid.isBlank() || pid <= 0 || startTicks < 0 || executableInode < 0) {
                throw new IllegalArgumentException("Incomplete process identity");
            }
        }
    }
    public record Shield(String name, String payloadDigest) {
        public Shield {
            if (name == null || !name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,254}")
                    || payloadDigest == null || !payloadDigest.matches("[a-fA-F0-9]{64}")) {
                throw new IllegalArgumentException("Invalid intended shield");
            }
            payloadDigest = payloadDigest.toLowerCase(java.util.Locale.ROOT);
        }
    }
    public record Intent(Identity process, Shield shield) {
        public Intent { Objects.requireNonNull(process); Objects.requireNonNull(shield); }
    }
    public enum HostState { VERIFIED_PRESENT, VERIFIED_RETIRED, FAILED, UNKNOWN }
    public enum Activation { EXPECTED_OPTION, OPTION_ABSENT, CONFLICTING_OPTIONS, INACCESSIBLE, UNKNOWN }
    public record Host(Shield shield, HostState state, Instant at) {
        public Host { Objects.requireNonNull(shield); Objects.requireNonNull(state); Objects.requireNonNull(at); }
    }
    public record Startup(Identity process, Activation activation, Instant at) {
        public Startup { Objects.requireNonNull(activation); Objects.requireNonNull(at); }
    }
    /** Qualified means a fresh challenge and passing control were bound to this process and shield. */
    public record Behavior(Identity process, Shield shield, Assessment.Behavior outcome,
                           boolean qualified, Instant at) {
        public Behavior {
            Objects.requireNonNull(shield); Objects.requireNonNull(outcome); Objects.requireNonNull(at);
        }
    }
    public record Result(Intent intent, Host host, Startup startup, Behavior behavior,
                         Assessment.Conclusion conclusion, List<String> reasons) {
        public Result { reasons = List.copyOf(reasons); }
    }

    private CoverageAssessment() {
    }

    public static Result assess(Intent intent, Host host, Startup startup, Behavior behavior,
                                Identity current, Instant now, Duration maxAge) {
        Objects.requireNonNull(intent);
        Objects.requireNonNull(now);
        if (maxAge.isNegative() || maxAge.isZero()) {
            throw new IllegalArgumentException("Evidence age must be positive");
        }
        var reasons = new java.util.ArrayList<String>();
        if (!intent.process().equals(current)) reasons.add("process_ended_or_identity_changed");
        if (host == null) reasons.add("host_evidence_missing");
        else {
            if (!intent.shield().equals(host.shield())) reasons.add("host_shield_conflict");
            if (!fresh(host.at(), now, maxAge)) reasons.add("host_evidence_stale_or_future");
            if (host.state() == HostState.FAILED || host.state() == HostState.UNKNOWN) {
                reasons.add("host_" + host.state().name().toLowerCase(java.util.Locale.ROOT));
            }
        }
        if (startup == null) reasons.add("startup_evidence_missing");
        else {
            if (!intent.process().equals(startup.process())) reasons.add("startup_identity_unavailable_or_changed");
            if (!fresh(startup.at(), now, maxAge)) reasons.add("startup_evidence_stale_or_future");
            if (startup.activation() == Activation.CONFLICTING_OPTIONS
                    || startup.activation() == Activation.INACCESSIBLE || startup.activation() == Activation.UNKNOWN) {
                reasons.add("startup_" + startup.activation().name().toLowerCase(java.util.Locale.ROOT));
            }
        }
        if (behavior == null) reasons.add("behavior_evidence_missing");
        else {
            if (!intent.process().equals(behavior.process())) reasons.add("behavior_identity_changed");
            if (!intent.shield().equals(behavior.shield())) reasons.add("behavior_shield_conflict");
            if (!fresh(behavior.at(), now, maxAge)) reasons.add("behavior_evidence_stale_or_future");
            if (!behavior.qualified() || behavior.outcome() == Assessment.Behavior.INCONCLUSIVE) {
                reasons.add("behavior_unqualified_or_inconclusive");
            }
        }
        Assessment.Conclusion conclusion = Assessment.Conclusion.UNKNOWN;
        if (reasons.isEmpty()) {
            Assessment.HostEvidence hostState = host.state() == HostState.VERIFIED_PRESENT
                    ? Assessment.HostEvidence.VERIFIED_PRESENT : Assessment.HostEvidence.VERIFIED_RETIRED;
            conclusion = Assessment.assess(hostState, behavior.outcome());
        }
        return new Result(intent, host, startup, behavior, conclusion, reasons);
    }

    private static boolean fresh(Instant at, Instant now, Duration age) {
        return !at.isAfter(now) && !at.isBefore(now.minus(age));
    }
}
