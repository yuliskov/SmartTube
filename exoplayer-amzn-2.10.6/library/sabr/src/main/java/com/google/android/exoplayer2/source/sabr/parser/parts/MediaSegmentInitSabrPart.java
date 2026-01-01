package com.google.android.exoplayer2.source.sabr.parser.parts;

import com.google.android.exoplayer2.source.sabr.parser.models.FormatSelector;
import com.google.android.exoplayer2.source.sabr.protos.misc.FormatId;

public class MediaSegmentInitSabrPart implements SabrPart {
    public final FormatSelector formatSelector;
    public final FormatId formatId;
    public final long playerTimeMs;
    public final long sequenceNumber;
    public final long totalSegments;
    public final int durationMs;
    public final boolean durationEstimated;
    public final int startBytes;
    public final int startTimeMs;
    public final boolean isInitSegment;
    public final long contentLength;
    public final boolean contentLengthEstimate;

    public MediaSegmentInitSabrPart(
            FormatSelector formatSelector,
            FormatId formatId,
            long playerTimeMs,
            long sequenceNumber,
            long totalSegments,
            int durationMs,
            boolean durationEstimated,
            int startBytes,
            int startTimeMs,
            boolean isInitSegment,
            long contentLength,
            boolean contentLengthEstimate) {
        this.formatSelector = formatSelector;
        this.formatId = formatId;
        this.playerTimeMs = playerTimeMs;
        this.sequenceNumber = sequenceNumber;
        this.totalSegments = totalSegments;
        this.durationMs = durationMs;
        this.durationEstimated = durationEstimated;
        this.startBytes = startBytes;
        this.startTimeMs = startTimeMs;
        this.isInitSegment = isInitSegment;
        this.contentLength = contentLength;
        this.contentLengthEstimate = contentLengthEstimate;
    }
}
