package dev.kintsugi.orchestrator;

import dev.kintsugi.assurance.CoverageAssessment.*;
import dev.kintsugi.shirudo.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic lifecycle contract tests. No native verification or protection is established here. */
class AttestationWindowTest {
    final Instant now = Instant.parse("2026-01-01T00:00:10Z");
    OperatorProfile profile(String nonce, HostState state, String chain) {
        return new OperatorProfile("/trusted/verifier", "/trusted/evidence", nonce,
                new VerificationPolicy("a".repeat(68), chain, "a".repeat(64), "a".repeat(64),
                        "a".repeat(64), 8, Map.of()),
                new OperatorProfile.InstantFields(now.minusSeconds(nonce.equals("aa") ? 5 : 1).toString(),
                        now.plusSeconds(20).toString()),
                new OperatorProfile.Mapping("boot", new Shield("sql", "a".repeat(64)), state));
    }
    OperatorProfile.Receipt receipt(OperatorProfile p, ShirudoCli.State state) {
        return new OperatorProfile.Receipt(new ShirudoCli.Evidence(state, "SYNTHETIC", "", "", null, now, List.of()),
                new Host(p.mapping().shield(), p.mapping().state(), now), p.policy().expectedChain(),
                new OperatorProfile.Epoch(1, 2, 3, 10));
    }
    @Test void acceptsConsistentNativeReceiptsContract() {
        var a = profile("aa", HostState.VERIFIED_PRESENT, "a".repeat(64));
        var b = profile("bb", HostState.VERIFIED_PRESENT, "a".repeat(64));
        assertTrue(AttestationWindow.validate(a, receipt(a, ShirudoCli.State.VERIFIED), b,
                receipt(b, ShirudoCli.State.VERIFIED), now.minusSeconds(2), now).isEmpty());
    }
    @Test void detectsChangedMeasurementsAndRetirement() {
        var a = profile("aa", HostState.VERIFIED_PRESENT, "a".repeat(64));
        var b = profile("bb", HostState.VERIFIED_RETIRED, "b".repeat(64));
        var reasons = AttestationWindow.validate(a, receipt(a, ShirudoCli.State.VERIFIED), b,
                receipt(b, ShirudoCli.State.VERIFIED), now.minusSeconds(2), now);
        assertTrue(reasons.contains("shield_changed_or_retired"));
        assertTrue(reasons.contains("host_measurements_changed"));
    }
    @Test void rejectsMissingFailedReplayedAndPrematureClosing() {
        var a = profile("aa", HostState.VERIFIED_PRESENT, "a".repeat(64));
        var first = receipt(a, ShirudoCli.State.VERIFIED);
        assertTrue(AttestationWindow.validate(a, first, null, null, now, now)
                .contains("closing_host_attestation_missing"));
        var reasons = AttestationWindow.validate(a, first, a, receipt(a, ShirudoCli.State.FAILED), now, now);
        assertTrue(reasons.contains("closing_attestation_unverified"));
        assertTrue(reasons.contains("attestation_nonce_reused"));
        assertTrue(reasons.contains("attestation_window_invalid"));
    }
    @Test void rejectsUnavailableOrChangedEpochAndExpiredWindow() {
        var a = profile("aa", HostState.VERIFIED_PRESENT, "a".repeat(64));
        var b = profile("bb", HostState.VERIFIED_PRESENT, "a".repeat(64));
        var last = receipt(b, ShirudoCli.State.VERIFIED);
        var missing = new OperatorProfile.Receipt(last.verification(), last.approved(), last.chain(), null);
        assertTrue(AttestationWindow.validate(a, receipt(a, ShirudoCli.State.VERIFIED), b, missing,
                now.minusSeconds(2), now).contains("authenticated_tpm_epoch_unavailable"));
        var changed = new OperatorProfile.Receipt(last.verification(), last.approved(), last.chain(),
                new OperatorProfile.Epoch(2, 2, 3, 10));
        assertTrue(AttestationWindow.validate(a, receipt(a, ShirudoCli.State.VERIFIED), b, changed,
                now.minusSeconds(2), now.plusSeconds(30)).contains("tpm_epoch_changed"));
        assertTrue(AttestationWindow.validate(a, receipt(a, ShirudoCli.State.VERIFIED), b, last,
                now.minusSeconds(2), now.plusSeconds(30)).contains("attestation_window_invalid"));
    }
}
