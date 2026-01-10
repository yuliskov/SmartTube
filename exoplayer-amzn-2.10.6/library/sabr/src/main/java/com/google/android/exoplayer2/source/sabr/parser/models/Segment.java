package com.google.android.exoplayer2.source.sabr.parser.models;

import com.google.android.exoplayer2.source.sabr.protos.misc.FormatId;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.MediaHeader;

public class Segment {
    public final MediaHeader mediaHeader;
    public final FormatId formatId;
    public final boolean isInitSegment;
    public final long durationMs;
    public final long startRange;
    public final int sequenceNumber;
    public final long contentLength;
    public final boolean contentLengthEstimated;
    public final long startMs;
    public final SelectedFormat initializedFormat;
    public final boolean durationEstimated;
    public final boolean discard;
    public final boolean consumed;
    public final long sequenceLmt;
    public int receivedDataLength;

    public Segment(MediaHeader mediaHeader,
                   FormatId formatId,
                   boolean isInitSegment,
                   long durationMs,
                   long startRange,
                   int sequenceNumber,
                   long contentLength,
                   boolean contentLengthEstimated,
                   long startMs,
                   SelectedFormat initializedFormat,
                   boolean durationEstimated,
                   boolean discard,
                   boolean consumed,
                   long sequenceLmt) {
        this.mediaHeader = mediaHeader;
        this.formatId = formatId;
        this.isInitSegment = isInitSegment;
        this.durationMs = durationMs;
        this.startRange = startRange;
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
