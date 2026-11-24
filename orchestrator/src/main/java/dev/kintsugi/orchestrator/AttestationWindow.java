package dev.kintsugi.orchestrator;

import dev.kintsugi.shirudo.ShirudoCli;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Compares operator policies only after the native verifier has checked both original envelopes. */
final class AttestationWindow {
    static List<String> validate(OperatorProfile open, OperatorProfile.Receipt first,
                                OperatorProfile close, OperatorProfile.Receipt last,
                                Instant finished, Instant now) {
        var reasons = new ArrayList<String>();
        if (open == null || first == null || first.verification().state() != ShirudoCli.State.VERIFIED)
            reasons.add("opening_attestation_unverified");
        if (close == null || last == null) {
            reasons.add("closing_host_attestation_missing");
            return reasons;
        }
        if (last.verification().state() != ShirudoCli.State.VERIFIED)
            reasons.add("closing_attestation_unverified");
        if (open == null || first == null) return reasons;
        if (open.nonce().equalsIgnoreCase(close.nonce())) reasons.add("attestation_nonce_reused");
        Instant issued = Instant.parse(close.window().issued());
        if (first.verification().collectedAt().isAfter(finished)
                || first.verification().collectedAt().isBefore(Instant.parse(open.window().issued()))
                || issued.isAfter(now) || issued.isBefore(finished) || last.verification().collectedAt().isBefore(issued)
                || last.verification().collectedAt().isAfter(now)
                || now.isAfter(Instant.parse(close.window().expires()))
                || now.isAfter(Instant.parse(open.window().expires())))
            reasons.add("attestation_window_invalid");
        if (!open.policy().akName().equalsIgnoreCase(close.policy().akName()))
            reasons.add("attestation_host_changed");
        if (!open.policy().equals(close.policy())) reasons.add("host_measurements_changed");
        if (first.approved() == null || last.approved() == null
                || open.mapping() == null || !open.mapping().equals(close.mapping()))
            reasons.add("shield_changed_or_retired");
        if (first.epoch() == null || last.epoch() == null) reasons.add("authenticated_tpm_epoch_unavailable");
        else {
            var a = first.epoch();
            var b = last.epoch();
            if (a.reset() != b.reset() || a.restart() != b.restart() || a.firmware() != b.firmware()
                    || Long.compareUnsigned(b.clock(), a.clock()) < 0) reasons.add("tpm_epoch_changed");
        }
        return List.copyOf(reasons);
    }
}
