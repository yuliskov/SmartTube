package com.google.android.exoplayer2.source.sabr.parser.models;

public class ConsumedRange {
    public long startSequenceNumber;
    public long endSequenceNumber;
    public long startTimeMs;
    public long durationMs;

    public ConsumedRange(long startTimeMs, long durationMs, long startSequenceNumber, long endSequenceNumber) {
        this.startTimeMs = startTimeMs;
        this.durationMs = durationMs;
        this.startSequenceNumber = startSequenceNumber;
        this.endSequenceNumber = endSequenceNumber;
    }
}
