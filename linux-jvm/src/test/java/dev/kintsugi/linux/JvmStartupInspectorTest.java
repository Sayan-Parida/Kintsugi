package dev.kintsugi.linux;

import dev.kintsugi.assurance.CoverageAssessment.Activation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JvmStartupInspectorTest {
    @TempDir Path proc;

    @Test void missingProcessCannotAcquireStartupEvidence() {
        var snapshot = new JvmInventory.Snapshot("boot", List.of(), 0, false);
        var result = new JvmStartupInspector(proc).inspect(snapshot,
                new JvmInventory.Candidate(12, 99), proc.resolve("agent.jar"));
        assertEquals(Activation.INACCESSIBLE, result.activation());
        assertNull(result.process());
    }

    @Test void optionsAreOnlyClassifiedAsStartupClues() {
        Path agent = Path.of("/run/shirudo-java/java-sql-inj.jar");
        assertEquals(Activation.EXPECTED_OPTION,
                JvmStartupInspector.classify("java\0-javaagent:" + agent + "\0Main", "", agent));
        assertEquals(Activation.EXPECTED_OPTION,
                JvmStartupInspector.classify("java\0Main", "JAVA_TOOL_OPTIONS=-javaagent:" + agent, agent));
        assertEquals(Activation.OPTION_ABSENT, JvmStartupInspector.classify("java\0Main", "", agent));
        assertEquals(Activation.CONFLICTING_OPTIONS,
                JvmStartupInspector.classify("java\0-javaagent:/other.jar", "", agent));
        assertEquals(Activation.UNKNOWN,
                JvmStartupInspector.classify("java", "JAVA_TOOL_OPTIONS='-javaagent:" + agent + "'", agent));
    }
}
