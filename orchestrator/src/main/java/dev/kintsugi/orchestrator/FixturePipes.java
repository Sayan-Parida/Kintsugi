package dev.kintsugi.orchestrator;

import dev.kintsugi.assurance.CoverageAssessment.*;
import dev.kintsugi.linux.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.*;

/** FD 0 receives the authorized child's stdout. FD 3 sends CHECK to its stdin. */
final class FixturePipes {
    private static final java.util.concurrent.atomic.AtomicBoolean ACTIVE = new java.util.concurrent.atomic.AtomicBoolean();
    private static volatile boolean poisoned;
    static Result assess(Path proc, CoverageService service, Intent intent, JvmInventory.Snapshot inventory,
                         JvmInventory.Candidate candidate, Path agent, OperatorProfile.Receipt receipt) throws Exception {
        if (poisoned || !ACTIVE.compareAndSet(false, true)) throw new IOException("Fixture channel is unavailable");
        try {
            return assessExclusive(proc, service, intent, inventory, candidate, agent, receipt);
        } finally { ACTIVE.set(false); }
    }

    private static Result assessExclusive(Path proc, CoverageService service, Intent intent, JvmInventory.Snapshot inventory,
                         JvmInventory.Candidate candidate, Path agent, OperatorProfile.Receipt receipt) throws Exception {
        var child = proc.resolve(Long.toString(candidate.pid())).resolve("fd");
        Path writer = proc.resolve("self/fd/3");
        samePipe(proc.resolve("self/fd/0"), child.resolve("1"));
        samePipe(writer, child.resolve("0"));

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        var task = new FutureTask<Result>(() -> {
            var ready = record(System.in);
            var session = service.openFixtureSession(true, intent, receipt.approved(), receipt.chain(),
                    receipt.verification(), ready, Duration.ofMinutes(1));
            var before = new JvmStartupInspector(proc).identity(candidate.pid());
            if (!before.equals(intent.process())) throw new IOException();
            if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline) throw new IOException();
            try (var out = Files.newOutputStream(writer, StandardOpenOption.WRITE)) {
                out.write((session.command() + "\n").getBytes(StandardCharsets.US_ASCII));
            }
            return service.assessFixture(intent, inventory, candidate, agent, receipt.approved(), receipt.chain(),
                    receipt.verification(), session, record(System.in), before, Duration.ofMinutes(1));
        });
        Thread.startVirtualThread(task);
        try { return task.get(5, TimeUnit.SECONDS); }
        catch (TimeoutException | InterruptedException unavailable) {
            poisoned = true;
            throw unavailable;
        }
        finally { task.cancel(true); }
    }

    private static void samePipe(Path ours, Path theirs) throws IOException {
        Path link = Files.readSymbolicLink(ours);
        if (!link.toString().matches("pipe:\\[[0-9]+\\]") || !link.equals(Files.readSymbolicLink(theirs)))
            throw new IOException();
    }
    private static String record(InputStream in) throws IOException {
        var bytes = new ByteArrayOutputStream();
        while (bytes.size() < 8192) {
            int b = in.read();
            if (b == '\n') return bytes.toString(StandardCharsets.UTF_8);
            if (b < 0) break;
            bytes.write(b);
        }
        throw new IOException();
    }
}
