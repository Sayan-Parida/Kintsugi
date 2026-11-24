package dev.kintsugi.shirudo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

final class CliProcess {
    enum Failure { NONE, UNSUPPORTED, MISSING_EXECUTABLE, PERMISSION_DENIED, TIMEOUT, OUTPUT_LIMIT, IO_ERROR, INTERRUPTED }
    record Result(int exitCode, String stdout, String stderr, Failure failure) {
    }

    private static final int MAX_BYTES = 8 * 1024 * 1024;

    static Result run(List<String> command, Duration timeout) {
        Process process = null;
        var readers = Executors.newVirtualThreadPerTaskExecutor();
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.environment().clear();
            builder.environment().put("LANG", "C");
            // Windows needs SystemRoot for some child executables used by portable tests.
            if (System.getenv("SystemRoot") != null) {
                builder.environment().put("SystemRoot", System.getenv("SystemRoot"));
            }
            try {
                process = builder.start();
            } catch (IOException unavailable) {
                Path executable = Path.of(command.getFirst());
                Failure failure = !java.nio.file.Files.exists(executable) ? Failure.MISSING_EXECUTABLE
                        : !java.nio.file.Files.isExecutable(executable) ? Failure.PERMISSION_DENIED : Failure.IO_ERROR;
                return new Result(-1, "", "", failure);
            }
            process.getOutputStream().close();
            Process child = process;
            Future<String> out = readers.submit(() -> read(child.getInputStream(), child));
            Future<String> err = readers.submit(() -> read(child.getErrorStream(), child));
            long start = System.nanoTime();
            if (!process.waitFor(timeout.toNanos(), TimeUnit.NANOSECONDS)) {
                stop(process);
                return new Result(-1, "", "", Failure.TIMEOUT);
            }
            long remaining = Math.max(1, timeout.toNanos() - (System.nanoTime() - start));
            String stdout = out.get(remaining, TimeUnit.NANOSECONDS);
            remaining = Math.max(1, timeout.toNanos() - (System.nanoTime() - start));
            return new Result(process.exitValue(), stdout, err.get(remaining, TimeUnit.NANOSECONDS), Failure.NONE);
        } catch (ExecutionException failed) {
            return new Result(-1, "", "", failed.getCause() instanceof OutputLimit
                    ? Failure.OUTPUT_LIMIT : Failure.IO_ERROR);
        } catch (TimeoutException timeoutFailure) {
            return new Result(-1, "", "", Failure.TIMEOUT);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new Result(-1, "", "", Failure.INTERRUPTED);
        } catch (IOException failed) {
            return new Result(-1, "", "", Failure.IO_ERROR);
        } finally {
            if (process != null) {
                stop(process);
            }
            readers.shutdownNow();
        }
    }

    private static String read(InputStream stream, Process child) throws IOException {
        try (stream; var bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            for (int count; (count = stream.read(buffer)) >= 0;) {
                if (bytes.size() + count > MAX_BYTES) {
                    stop(child);
                    throw new OutputLimit();
                }
                bytes.write(buffer, 0, count);
            }
            return bytes.toString(StandardCharsets.UTF_8);
        }
    }

    private static void stop(Process child) {
        child.descendants().forEach(ProcessHandle::destroyForcibly);
        child.destroyForcibly();
        try {
            child.getInputStream().close();
            child.getErrorStream().close();
        } catch (IOException ignored) {
            // A terminated process may have already closed its pipes.
        }
    }

    private static final class OutputLimit extends IOException {
    }
}
