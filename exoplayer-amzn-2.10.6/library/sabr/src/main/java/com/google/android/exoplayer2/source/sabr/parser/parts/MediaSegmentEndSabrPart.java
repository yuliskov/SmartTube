package com.google.android.exoplayer2.source.sabr.parser.parts;

import com.google.android.exoplayer2.source.sabr.parser.models.FormatSelector;
import com.google.android.exoplayer2.source.sabr.protos.misc.FormatId;

public class MediaSegmentEndSabrPart implements SabrPart {
    public final FormatSelector formatSelector;
    public final FormatId formatId;
    public final long sequenceNumber;
    public final boolean isInitSegment;
    public final long totalSegments;
    public final long startTimeMs;
    public final long durationMs;

    public MediaSegmentEndSabrPart(
            FormatSelector formatSelector,
            FormatId formatId,
            long sequenceNumber,
            boolean isInitSegment,
            long totalSegments,
            long startTimeMs,
            long durationMs) {
        this.formatSelector = formatSelector;
        this.formatId = formatId;
        this.sequenceNumber = sequenceNumber;
        this.isInitSegment = isInitSegment;
        this.totalSegments = totalSegments;
        this.startTimeMs = startTimeMs;
        this.durationMs = durationMs;
    }
}
