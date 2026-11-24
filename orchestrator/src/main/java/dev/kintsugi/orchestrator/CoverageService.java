package dev.kintsugi.orchestrator;

import dev.kintsugi.assurance.CoverageAssessment;
import dev.kintsugi.assurance.FixtureAssessmentSession;
import dev.kintsugi.assurance.CoverageAssessment.*;
import dev.kintsugi.linux.JvmInventory;
import dev.kintsugi.linux.JvmStartupInspector;
import dev.kintsugi.shirudo.ShirudoCli;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/** Application entry point for one declared workload lifetime. */
public final class CoverageService {
    private final JvmStartupInspector inspector;

    public CoverageService(JvmStartupInspector inspector) {
        this.inspector = inspector;
    }

    /** Caller owns the fixture channel and authorizes only this exact test process. */
    public FixtureAssessmentSession openFixtureSession(boolean authorized, Intent intent, Host approvedHost,
            String approvedChain, ShirudoCli.Evidence verification, String ready, Duration ttl) throws IOException {
        Host host = verifiedHost(approvedHost, approvedChain, verification);
        if (host == null) throw new IllegalArgumentException("Missing verified host mapping");
        return new FixtureAssessmentSession(authorized, intent, host, inspector.identity(intent.process().pid()),
                ready, Instant.now(), ttl);
    }

    /** response comes only from the authorized fixture's dedicated channel; null represents no response. */
    public Result assessFixture(Intent intent, JvmInventory.Snapshot inventory, JvmInventory.Candidate candidate,
            Path agent, Host approvedHost, String approvedChain, ShirudoCli.Evidence verification,
            FixtureAssessmentSession session, String response, Identity beforeChallenge, Duration maxAge) {
        Identity after = null;
        try { after = inspector.identity(candidate.pid()); }
        catch (IOException | IllegalArgumentException | UnsupportedOperationException unavailable) { }
        Behavior behavior = session == null ? null : session.accept(response, beforeChallenge, after, Instant.now());
        return assess(intent, inventory, candidate, agent, approvedHost, approvedChain, verification, behavior, maxAge);
    }

    /**
     * approvedHost is an operator-owned mapping from a verified expected chain to the intended shield.
     * The caller must supply the matching native verifier receipt for that profile and observation window.
     */
    public Result assess(Intent intent, JvmInventory.Snapshot inventory, JvmInventory.Candidate candidate,
                         Path agent, Host approvedHost, String approvedChain, ShirudoCli.Evidence verification,
                         Behavior behavior, Duration maxAge) {
        if (!agent.equals(Path.of("/run/shirudo-java", intent.shield().name() + ".jar"))) {
            throw new IllegalArgumentException("Expected the intended shield's sealed agent path");
        }
        Host host = verifiedHost(approvedHost, approvedChain, verification);
        Startup startup = inspector.inspect(inventory, candidate, agent);
        Identity current = null;
        try {
            current = inspector.identity(candidate.pid());
        } catch (IOException | IllegalArgumentException | UnsupportedOperationException unavailable) {
            // Missing identity prevents an old PID's evidence from being reused.
        }
        return CoverageAssessment.assess(intent, host, startup, behavior, current, Instant.now(), maxAge);
    }

    private static Host verifiedHost(Host approvedHost, String approvedChain, ShirudoCli.Evidence verification) {
        Host host = null;
        if (approvedHost != null && verification != null) {
            HostState state = switch (verification.state()) {
                case VERIFIED -> approvedChain != null && approvedChain.matches("[a-fA-F0-9]{64}")
                        && verification.command().contains("--expect-chain=" + approvedChain)
                        ? approvedHost.state() : HostState.UNKNOWN;
                case FAILED -> HostState.FAILED;
                case UNKNOWN -> HostState.UNKNOWN;
            };
            host = new Host(approvedHost.shield(), state,
                    approvedHost.at().isBefore(verification.collectedAt())
                            ? approvedHost.at() : verification.collectedAt());
        }
        return host;
    }
}
