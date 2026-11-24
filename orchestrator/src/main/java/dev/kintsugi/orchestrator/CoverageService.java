package dev.kintsugi.orchestrator;

import dev.kintsugi.assurance.CoverageAssessment;
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
        Startup startup = inspector.inspect(inventory, candidate, agent);
        Identity current = null;
        try {
            current = inspector.identity(candidate.pid());
        } catch (IOException | IllegalArgumentException | UnsupportedOperationException unavailable) {
            // Missing identity prevents an old PID's evidence from being reused.
        }
        return CoverageAssessment.assess(intent, host, startup, behavior, current, Instant.now(), maxAge);
    }
}
