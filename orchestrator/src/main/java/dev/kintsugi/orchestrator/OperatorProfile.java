package dev.kintsugi.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kintsugi.assurance.CoverageAssessment.*;
import dev.kintsugi.shirudo.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;

/** Independently enrolled operator inputs. The native verifier decides the verdict. */
record OperatorProfile(String verifier, String evidence, String nonce, VerificationPolicy policy,
                       InstantFields window, Mapping mapping) {
    record InstantFields(String issued, String expires) {}
    record Mapping(String bootId, Shield shield, HostState state) {}
    record Receipt(ShirudoCli.Evidence verification, Host approved, String chain) {}

    static OperatorProfile load(Path path, ObjectMapper json) throws IOException {
        byte[] bytes;
        try (var in = Files.newInputStream(path)) { bytes = in.readNBytes(65537); }
        if (bytes.length > 65536) throw new IllegalArgumentException();
        var tree = json.readTree(bytes);
        if (tree == null || !tree.path("policy").path("configPcr").isInt())
            throw new IllegalArgumentException();
        OperatorProfile p = json.treeToValue(tree, OperatorProfile.class);
        if (p == null || p.policy == null || p.window == null || p.nonce == null
                || !p.nonce.matches("(?:[0-9a-fA-F]{2}){16,64}")
                || !Path.of(p.evidence).isAbsolute() || !Path.of(p.verifier).isAbsolute())
            throw new IllegalArgumentException();
        Instant start = Instant.parse(p.window.issued), end = Instant.parse(p.window.expires);
        if (!end.isAfter(start) || end.isAfter(start.plusSeconds(60))) throw new IllegalArgumentException();
        if (p.mapping != null && (p.mapping.shield == null || p.mapping.bootId == null || p.mapping.bootId.isBlank()
                || (p.mapping.state != HostState.VERIFIED_PRESENT && p.mapping.state != HostState.VERIFIED_RETIRED)))
            throw new IllegalArgumentException();
        return p;
    }

    boolean current() {
        Instant now = Instant.now();
        return !now.isBefore(Instant.parse(window.issued)) && now.isBefore(Instant.parse(window.expires));
    }

    Receipt verify(String boot) {
        var cli = new ShirudoCli(Path.of(verifier), Duration.ofSeconds(10));
        var result = cli.verify(Path.of(evidence), nonce, policy);
        Host host = mapping != null && mapping.bootId.equals(boot)
                ? new Host(mapping.shield, mapping.state, Instant.parse(window.issued)) : null;
        return new Receipt(result, host, policy.expectedChain());
    }
}
