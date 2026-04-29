package com.example.awveilcompat.probe;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Writes probe events to TSV log files in the probes directory.
 * Auto-creates the probes directory on first write.
 * Uses PrintWriter with auto-flush for crash-safe log output.
 */
public class ProbeLogger implements AutoCloseable {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path PROBES_DIR = Path.of("probes");

    private final PrintWriter writer;
    private final String sourceName;

    public ProbeLogger(String sourceName) {
        this.sourceName = sourceName;
        try {
            Files.createDirectories(PROBES_DIR);
            Path logFile = PROBES_DIR.resolve(sourceName + "-probe.log");
            this.writer = new PrintWriter(
                Files.newBufferedWriter(logFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.WRITE
                )
            );
            // Write header on first open
            writer.println(ProbeData.tsvHeader());
            writer.flush();
            LOGGER.info("AW-Veil Probe: {} logging to {}", sourceName, logFile.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create probe logger for " + sourceName, e);
        }
    }

    public void write(ProbeData event) {
        writer.println(event.toTsvLine());
        writer.flush();
    }

    @Override
    public void close() {
        writer.close();
    }
}
