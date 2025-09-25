package com.google.android.exoplayer2.source.sabr.parser.models;

public class ConsumedRange {
    public long startSequenceNumber;
    public long endSequenceNumber;
    public int startTimeMs;
    public int durationMs;

    public ConsumedRange(int startTimeMs, int durationMs, long startSequenceNumber, long endSequenceNumber) {
        this.startTimeMs = startTimeMs;
        this.durationMs = durationMs;
        this.startSequenceNumber = startSequenceNumber;
        this.endSequenceNumber = endSequenceNumber;
    }
}
