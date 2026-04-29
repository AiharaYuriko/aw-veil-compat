package com.example.awveilcompat.probe;

/**
 * Immutable record of a single probe event.
 * Written to TSV log by ProbeLogger.
 */
public class ProbeData {

    private final long nanoTime;
    private final String eventType;
    private final String data;  // tab-separated key=value pairs

    public ProbeData(long nanoTime, String eventType, String data) {
        this.nanoTime = nanoTime;
        this.eventType = eventType;
        this.data = data;
    }

    public long getNanoTime() { return nanoTime; }
    public String getEventType() { return eventType; }
    public String getData() { return data; }

    /**
     * Formats as TSV line: nanoTime\teventType\tkey=value\tkey=value...
     */
    public String toTsvLine() {
        return nanoTime + "\t" + eventType + "\t" + data;
    }

    /**
     * Returns TSV header for the probe log file.
     */
    public static String tsvHeader() {
        return "# nanoTime\teventType\tdata";
    }
}
