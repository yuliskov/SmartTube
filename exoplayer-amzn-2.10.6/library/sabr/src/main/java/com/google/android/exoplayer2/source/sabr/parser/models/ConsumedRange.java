package com.google.android.exoplayer2.source.sabr.parser.models;

public class ConsumedRange {
    public int startSequenceNumber;
    public int endSequenceNumber;
    public long startTimeMs;
    public long durationMs;

    public ConsumedRange(long startTimeMs, long durationMs, int startSequenceNumber, int endSequenceNumber) {
        this.startTimeMs = startTimeMs;
        this.durationMs = durationMs;
        this.startSequenceNumber = startSequenceNumber;
        this.endSequenceNumber = endSequenceNumber;
    }
}
