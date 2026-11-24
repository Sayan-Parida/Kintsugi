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
    record Receipt(ShirudoCli.Evidence verification, Host approved, String chain, Epoch epoch) {}
    record Epoch(long reset, long restart, long firmware, long clock) {}

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
        try {
            byte[] original = evidenceBytes();
            Path snapshot = Files.createTempFile("kintsugi-evidence-", ".json");
            try {
                Files.write(snapshot, original);
                return verifySnapshot(boot, snapshot, original);
            } finally { Files.deleteIfExists(snapshot); }
        } catch (IOException unavailable) {
            return new Receipt(new ShirudoCli.Evidence(ShirudoCli.State.UNKNOWN, "evidence_file_unavailable",
                    "", "", null, Instant.now(), java.util.List.of()), null, policy.expectedChain(), null);
        }
    }

    private Receipt verifySnapshot(String boot, Path snapshot, byte[] original) {
        var cli = new ShirudoCli(Path.of(verifier), Duration.ofSeconds(10));
        var result = cli.verify(snapshot, nonce, policy);
        Epoch epoch = null;
        try {
            if (result.state() == ShirudoCli.State.VERIFIED) {
                var json = new ObjectMapper().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
                var document = json.readTree(original);
                var b = java.nio.ByteBuffer.wrap(java.util.HexFormat.of().parseHex(
                        document.path("quote").path("attest").asText()));
                if (b.getInt() != 0xff544347 || b.getShort() != (short)0x8018) throw new IllegalArgumentException();
                for (int i = 0; i < 2; i++) {
                    int length = Short.toUnsignedInt(b.getShort());
                    b.position(Math.addExact(b.position(), length));
                }
                long clock = b.getLong();
                long reset = Integer.toUnsignedLong(b.getInt());
                long restart = Integer.toUnsignedLong(b.getInt());
                if (b.get() != 1) throw new IllegalArgumentException();
                epoch = new Epoch(reset, restart, b.getLong(), clock);
            }
        } catch (IOException | RuntimeException invalid) { }
        Host host = mapping != null && mapping.bootId.equals(boot)
                ? new Host(mapping.shield, mapping.state, Instant.parse(window.issued)) : null;
        return new Receipt(result, host, policy.expectedChain(), epoch);
    }

    private byte[] evidenceBytes() throws IOException {
        try (var in = Files.newInputStream(Path.of(evidence))) {
            byte[] bytes = in.readNBytes(8 * 1024 * 1024 + 1);
            if (bytes.length > 8 * 1024 * 1024) throw new IOException();
            return bytes;
        }
    }
}
