package dev.kintsugi.assurance;

import dev.kintsugi.assurance.CoverageAssessment.*;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** One authorized challenge on an operator-owned fixture stdin/stdout channel. Not an attestation. */
public final class FixtureAssessmentSession {
    private final Intent intent;
    private final Host host;
    private final Map<String, String> ready;
    private final UUID nonce = UUID.randomUUID();
    private final Instant issued;
    private final Duration ttl;
    private boolean consumed;

    /**
     * The operator must own the channel and explicitly authorize this exact process as a test fixture.
     * host must be the independently verified operator mapping; fixture output never supplies a shield.
     * observed is a fresh OS identity captured after reading ready, not a fixture assertion.
     */
    public FixtureAssessmentSession(boolean authorized, Intent intent, Host host, Identity observed,
                                    String readyLine, Instant now, Duration ttl) {
        this.intent = Objects.requireNonNull(intent);
        this.host = Objects.requireNonNull(host);
        this.issued = Objects.requireNonNull(now);
        this.ttl = Objects.requireNonNull(ttl);
        if (!authorized || ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofMinutes(1)) > 0
                || !intent.process().equals(observed) || !intent.shield().equals(host.shield())
                || (host.state() != HostState.VERIFIED_PRESENT && host.state() != HostState.VERIFIED_RETIRED)
                || !fresh(host.at(), now)) throw new IllegalArgumentException("Unqualified fixture authorization");
        ready = parse(readyLine);
        if (!"ready".equals(ready.get("event")) || !identityMatches(ready)
                || !canonicalUuid(ready.get("instance")) || !ready.getOrDefault("label", "").matches("[A-Za-z0-9_-]{1,40}")
                || !validStart(ready.get("started"), now)) {
            throw new IllegalArgumentException("Incomplete or mismatched fixture ready record");
        }
    }

    public String command() { return "CHECK " + nonce; }

    /** Every response attempt consumes the challenge, including malformed responses and timeouts (null). */
    public synchronized Behavior accept(String line, Identity before, Identity after, Instant received) {
        Behavior unknown = new Behavior(intent.process(), host.shield(), Assessment.Behavior.INCONCLUSIVE, false, received);
        if (consumed) return unknown;
        consumed = true;
        try {
            Map<String, String> response = parse(line);
            if (!intent.process().equals(before) || !intent.process().equals(after)
                    || received.isBefore(issued) || !fresh(issued, received) || !fresh(host.at(), received)
                    || !"check".equals(response.get("event")) || !nonce.toString().equals(response.get("nonce"))
                    || !identityMatches(response)) return unknown;
            for (String key : new String[]{"label", "instance", "started"}) {
                if (!Objects.equals(ready.get(key), response.get(key))) return unknown;
            }
            Instant at = Instant.parse(response.get("at"));
            if (at.isBefore(issued) || at.isAfter(received)
                    || !"scalar".equals(response.get("control")) || !"scalar".equals(response.get("after_control"))
                    || !"ok".equals(response.get("health"))) return unknown;
            Assessment.Behavior outcome;
            if ("guard_exception".equals(response.get("discriminator")) && "guard_observed".equals(response.get("behavior"))) {
                outcome = Assessment.Behavior.GUARD_OBSERVED;
            } else if ("scalar".equals(response.get("discriminator")) && "baseline_observed".equals(response.get("behavior"))) {
                outcome = Assessment.Behavior.BASELINE_OBSERVED;
            } else return unknown;
            return new Behavior(intent.process(), host.shield(), outcome, true, at);
        } catch (RuntimeException invalid) {
            return unknown;
        }
    }

    private boolean fresh(Instant at, Instant now) { return !at.isAfter(now) && !at.isBefore(now.minus(ttl)); }
    private boolean identityMatches(Map<String, String> record) {
        Identity p = intent.process();
        return p.bootId().equals(record.get("boot")) && p.pidNamespace().equals(record.get("pid_namespace"))
                && Long.toString(p.pid()).equals(record.get("pid")) && Long.toString(p.startTicks()).equals(record.get("start_ticks"));
    }
    private static boolean canonicalUuid(String text) {
        try { return UUID.fromString(text).toString().equals(text); } catch (RuntimeException invalid) { return false; }
    }
    private static boolean validStart(String text, Instant now) {
        try { return !Instant.parse(text).isAfter(now); } catch (RuntimeException invalid) { return false; }
    }
    private static Map<String, String> parse(String line) {
        if (line == null || line.length() > 8192 || line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Missing or oversized fixture record");
        }
        Map<String, String> fields = new HashMap<>();
        for (String field : line.split("\t", -1)) {
            int equals = field.indexOf('=');
            if (equals <= 0 || equals == field.length() - 1
                    || fields.putIfAbsent(field.substring(0, equals), field.substring(equals + 1)) != null) {
                throw new IllegalArgumentException("Invalid or conflicting fixture fields");
            }
        }
        return fields;
    }
}
