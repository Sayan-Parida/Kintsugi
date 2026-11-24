package dev.kintsugi.orchestrator;

import dev.kintsugi.shirudo.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class EvidenceSnapshotTest {
    @TempDir Path dir;
    @Test void verifierUsesPrivateSnapshotAndDeletesIt() throws Exception {
        var original = dir.resolve("input.json");
        Files.writeString(original, "{}");
        var p = new OperatorProfile(dir.resolve("missing-verifier").toString(), original.toString(), "ab".repeat(16),
                new VerificationPolicy("a".repeat(68), "a".repeat(64), "a".repeat(64), "a".repeat(64),
                        "a".repeat(64), 8, Map.of()),
                new OperatorProfile.InstantFields(Instant.now().toString(), Instant.now().plusSeconds(30).toString()), null);
        var r = p.verify(null);
        assertEquals(ShirudoCli.State.UNKNOWN, r.verification().state());
        var arg = r.verification().command().stream().filter(s -> s.startsWith("--evidence=")).findFirst().orElseThrow();
        Path snapshot = Path.of(arg.substring("--evidence=".length()));
        assertNotEquals(original, snapshot);
        assertFalse(Files.exists(snapshot));
        assertEquals("{}", Files.readString(original));
        assertNull(r.epoch());
    }
}
