package dev.kintsugi.linux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Best-effort discovery within the observer's procfs view. */
public final class JvmInventory {
    public record Candidate(long pid, long startTicks) {
    }

    public record Snapshot(String bootId, List<Candidate> candidates, int unreadable, boolean truncated) {
        public Snapshot {
            candidates = List.copyOf(candidates);
        }
    }

    private final Path proc;

    public JvmInventory(Path proc) {
        this.proc = proc;
    }

    public Snapshot scan(Duration budget) throws IOException {
        long maxNanos = budget.toNanos();
        if (maxNanos <= 0) {
            throw new IllegalArgumentException("Scan budget must be positive");
        }
        long started = System.nanoTime();
        String bootId = Files.readString(proc.resolve("sys/kernel/random/boot_id")).strip();
        List<Candidate> candidates = new ArrayList<>();
        int unreadable = 0;
        boolean truncated = false;

        try (var entries = Files.newDirectoryStream(proc)) {
            for (Path entry : entries) {
                if (System.nanoTime() - started >= maxNanos) {
                    truncated = true;
                    break;
                }
                String name = entry.getFileName().toString();
                if (!name.matches("[1-9][0-9]{0,9}")) {
                    continue;
                }
                try {
                    Path executable = Files.readSymbolicLink(entry.resolve("exe"));
                    if (!executable.getFileName().toString().equals("java")) {
                        continue;
                    }
                    long ticks = startTicks(Files.readString(entry.resolve("stat")));
                    if (executable.equals(Files.readSymbolicLink(entry.resolve("exe")))
                            && ticks == startTicks(Files.readString(entry.resolve("stat")))) {
                        candidates.add(new Candidate(Long.parseLong(name), ticks));
                    } else {
                        unreadable++;
                    }
                } catch (NoSuchFileException ignored) {
                    // The process disappeared or has no executable (for example a kernel thread).
                } catch (IOException | IllegalArgumentException unreadableProcess) {
                    unreadable++;
                }
            }
        }
        candidates.sort(Comparator.comparingLong(Candidate::pid));
        return new Snapshot(bootId, candidates, unreadable, truncated);
    }

    static long startTicks(String stat) {
        // The parenthesized command may itself contain spaces and parentheses.
        int commEnd = stat.lastIndexOf(')');
        if (commEnd < 0) {
            throw new IllegalArgumentException("Missing process name in proc stat");
        }
        String[] fields = stat.substring(commEnd + 1).strip().split("\\s+");
        if (fields.length < 20) {
            throw new IllegalArgumentException("Missing start time in proc stat");
        }
        return Long.parseLong(fields[19]);
    }
}
