package com.google.android.exoplayer2.source.sabr.parser.models;

import com.google.android.exoplayer2.source.sabr.protos.misc.FormatId;

import java.util.ArrayList;
import java.util.List;

public class SelectedFormat {
    public final FormatId formatId;
    public final long durationMs;
    public final long endTimeMs;
    public final String mimeType;
    public final String videoId;
    public final FormatSelector formatSelector;
    public long totalSegments;
    public final boolean discard;
    public long sequenceLmt = -1;
    public Segment currentSegment;
    public Segment initSegment;
    public final List<ConsumedRange> consumedRanges = new ArrayList<>();

    public SelectedFormat(
            FormatId formatId,
            long durationMs,
            long endTimeMs,
            String mimeType,
            String videoId,
            FormatSelector formatSelector,
            long totalSegments,
            boolean discard
    ) {
        this.formatId = formatId;
        this.durationMs = durationMs;
        this.endTimeMs = endTimeMs;
        this.mimeType = mimeType;
        this.videoId = videoId;
        this.formatSelector = formatSelector;
        this.totalSegments = totalSegments;
        this.discard = discard;
    }
}
