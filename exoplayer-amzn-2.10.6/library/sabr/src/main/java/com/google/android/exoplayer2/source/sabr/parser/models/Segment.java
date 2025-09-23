package com.google.android.exoplayer2.source.sabr.parser.models;

import com.google.android.exoplayer2.source.sabr.protos.videostreaming.FormatId;

public class Segment {
    public final FormatId formatId;
    public final boolean isInitSegment;
    public final int durationMs;
    public final int startDataRange;
    public long sequenceNumber;
    public final long contentLength;
    public final boolean contentLengthEstimated;
    public final int startMs;
    public final InitializedFormat initializedFormat;
    public final boolean durationEstimated;
    public final boolean discard;
    public final boolean consumed;
    public final int sequenceLmt;
    public int receivedDataLength;

    public Segment(FormatId formatId,
                   boolean isInitSegment,
                   int durationMs,
                   int startDataRange,
                   long sequenceNumber,
                   long contentLength,
                   boolean contentLengthEstimated,
                   int startMs,
                   InitializedFormat initializedFormat,
                   boolean durationEstimated,
                   boolean discard,
                   boolean consumed,
                   int sequenceLmt) {
        this.formatId = formatId;
        this.isInitSegment = isInitSegment;
        this.durationMs = durationMs;
        this.startDataRange = startDataRange;
        this.sequenceNumber = sequenceNumber;
        this.contentLength = contentLength;
        this.contentLengthEstimated = contentLengthEstimated;
        this.startMs = startMs;
        this.initializedFormat = initializedFormat;
        this.durationEstimated = durationEstimated;
        this.discard = discard;
        this.consumed = consumed;
        this.sequenceLmt = sequenceLmt;
    }
}
