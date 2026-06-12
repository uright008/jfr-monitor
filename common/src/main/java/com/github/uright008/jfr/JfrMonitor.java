package com.github.uright008.jfr;

import jdk.jfr.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicReference;

/**
 * JFR diagnostic recording for mc-parallel performance analysis.
 *
 * <p>Default: 30 seconds, all threads, outputs to jfr/.
 * Fully non-blocking for interactive use; synchronous dump available via {@link #stopSync()}.
 */
public final class JfrMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger("mc-parallel:jfr");
    private static final String OUTPUT_DIR = "jfr";

    private static final AtomicReference<Recording> activeRecording = new AtomicReference<>();

    private JfrMonitor() {}

    public static void start(int seconds) {
        Recording recording = new Recording();
        recording.setName("mc-parallel-" + System.currentTimeMillis());

        recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofSeconds(1));
        recording.enable("jdk.ObjectAllocationSample").withPeriod(Duration.ofSeconds(1));
        recording.enable("jdk.CPULoad").withPeriod(Duration.ofSeconds(1));
        recording.enable("jdk.GCHeapSummary").withPeriod(Duration.ofSeconds(1));
        recording.enable("jdk.G1HeapSummary").withPeriod(Duration.ofSeconds(1));
        recording.enable("jdk.GCHeapMemoryUsage").withPeriod(Duration.ofSeconds(1));
        recording.enable("jdk.ThreadPark").withPeriod(Duration.ofSeconds(1));
        recording.enable("jdk.NativeLibrary").withPeriod(Duration.ofSeconds(1));

        recording.setMaxSize(256 * 1024 * 1024);
        recording.setMaxAge(Duration.ofSeconds(seconds + 60));
        recording.setDuration(Duration.ofSeconds(seconds));

        recording.start();

        if (!activeRecording.compareAndSet(null, recording)) {
            recording.stop();
            recording.close();
            LOGGER.warn("JFR recording already active — start request ignored");
            return;
        }

        LOGGER.info("JFR recording started ({} seconds, output: jfr/)", seconds);
    }

    /** Atomically claims the active recording, stops it, and dumps to disk on a background thread. */
    public static void stop() {
        Recording recording = activeRecording.getAndSet(null);
        if (recording == null) {
            LOGGER.warn("No active JFR recording");
            return;
        }
        try {
            recording.stop();
        } catch (IllegalStateException ignored) {
            // already stopped by setDuration()
        }

        String filename = newFilename();

        Thread dumpThread = new Thread(() -> {
            try {
                dumpAndClose(recording, filename);
            } catch (IOException e) {
                LOGGER.error("Failed to save JFR recording", e);
            }
        }, "jfr-dump");
        dumpThread.setDaemon(true);
        dumpThread.start();
    }

    /** Synchronous version: stops, dumps, and waits for the dump to complete. Returns the output path. */
    public static Path stopSync() {
        Recording recording = activeRecording.getAndSet(null);
        if (recording == null) {
            LOGGER.warn("No active JFR recording");
            return null;
        }
        try {
            recording.stop();
        } catch (IllegalStateException ignored) {
            // already stopped by setDuration()
        }

        String filename = newFilename();
        try {
            Path path = dumpAndClose(recording, filename);
            LOGGER.info("JFR recording saved to: {}", path.toAbsolutePath());
            return path;
        } catch (IOException e) {
            LOGGER.error("Failed to save JFR recording", e);
            return null;
        }
    }

    public static void autoRecord(int seconds) {
        LOGGER.info("JFR auto-recording starting ({} seconds)...", seconds);
        start(seconds);

        Thread timerThread = new Thread(() -> {
            try {
                Thread.sleep(seconds * 1000L);
                stop();
                LOGGER.info("JFR auto-recording complete");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "jfr-auto-dump");
        timerThread.setDaemon(true);
        timerThread.start();
    }

    public static boolean isRecording() {
        return activeRecording.get() != null;
    }

    // ── internal ─────────────────────────────────

    private static String newFilename() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        return "jfr-" + timestamp + ".jfr";
    }

    private static Path dumpAndClose(Recording recording, String filename) throws IOException {
        Path outDir = Path.of(OUTPUT_DIR);
        outDir.toFile().mkdirs();
        Path outPath = outDir.resolve(filename);
        recording.dump(outPath);
        recording.close();
        return outPath;
    }
}
