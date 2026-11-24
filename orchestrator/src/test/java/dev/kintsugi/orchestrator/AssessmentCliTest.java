package dev.kintsugi.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AssessmentCliTest {
    @TempDir Path dir;
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    final ObjectMapper json = new ObjectMapper();

    int run(boolean linux, String... args) {
        bytes.reset();
        return new AssessmentCli(dir, linux).run(args, new PrintStream(bytes));
    }

    @Test void helpAndUsageErrorsDoNotEchoArguments() {
        assertEquals(0, run(false, "--help"));
        assertTrue(bytes.toString().contains("verify-host"));
        assertEquals(2, run(false, "assess", "--token=private", "--json"));
        assertFalse(bytes.toString().contains("private"));
        assertEquals(2, run(false, "verify-host"));
        assertEquals(2, run(false, "inventory", "--json", "--json"));
    }

    @Test void discoversAndReportsMissingTarget() throws Exception {
        Files.createDirectories(dir.resolve("sys/kernel/random"));
        Files.writeString(dir.resolve("sys/kernel/random/boot_id"), "test-boot");
        assertEquals(0, run(true, "inventory", "--json"));
        assertEquals(0, json.readTree(bytes.toByteArray()).required("jvms").size());
        assertEquals(3, run(true, "assess", "--pid=42", "--shield=sql", "--digest=" + "b".repeat(64), "--json"));
        var report = json.readTree(bytes.toByteArray());
        assertEquals("jvm_not_visible", report.required("reasons").get(0).asText());
        assertEquals("UNKNOWN", report.required("coverage").asText());
        assertTrue(report.has("startup") && report.has("behaviour") && report.has("hostVerification"));
    }

    @Test void verifiesOnlyCurrentCompleteProfiles() throws Exception {
        var file = dir.resolve("profile.json");
        var root = json.createObjectNode();
        root.put("verifier", dir.resolve("missing-verifier").toString());
        root.put("evidence", dir.resolve("missing-evidence").toString());
        root.put("nonce", "ab".repeat(16));
        var policy = root.putObject("policy");
        policy.put("akName", "a".repeat(68));
        for (String key : new String[]{"expectedChain","bindPcr9","bindPcr11","configRoot"})
            policy.put(key, "a".repeat(64));
        policy.putObject("bootPcrs");
        policy.put("configPcr", 8);
        var now = java.time.Instant.now();
        var window = root.putObject("window");
        window.put("issued", now.minusSeconds(1).toString());
        window.put("expires", now.plusSeconds(45).toString());
        json.writeValue(file.toFile(), root);
        assertEquals(3, run(true, "verify-host", "--profile=" + file, "--json"));
        assertEquals("evidence_file_unavailable", json.readTree(bytes.toByteArray()).required("hostReason").asText());
        assertFalse(bytes.toString().contains("ab".repeat(16)));
        window.put("issued", now.minusSeconds(50).toString());
        window.put("expires", now.minusSeconds(10).toString());
        json.writeValue(file.toFile(), root);
        assertEquals(3, run(true, "verify-host", "--profile=" + file, "--json"));
        assertTrue(bytes.toString().contains("challenge_window_invalid"));
        policy.remove("configPcr");
        json.writeValue(file.toFile(), root);
        assertEquals(2, run(true, "verify-host", "--profile=" + file));
    }

    @Test void rejectsUnsupportedProcfsAndUnconfiguredFixture() {
        assertEquals(3, run(false, "inventory", "--json"));
        assertTrue(bytes.toString().contains("unsupported_environment"));
        assertEquals(2, run(false, "assess", "--fixture-pipes", "--pid=42", "--shield=sql",
                "--digest=" + "a".repeat(64)));
    }
}
