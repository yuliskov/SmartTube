package com.google.android.exoplayer2.source.sabr.parser.models;

import com.google.android.exoplayer2.source.sabr.protos.videostreaming.FormatId;

import java.util.ArrayList;
import java.util.List;

public class InitializedFormat {
    public final FormatId formatId;
    public final int durationMs;
    public final int endTimeMs;
    public final String mimeType;
    public final String videoId;
    public final FormatSelector formatSelector;
    public int totalSegments;
    public final boolean discard;
    public int sequenceLmt = -1;
    public Segment currentSegment;
    public Segment initSegment;
    public final List<ConsumedRange> consumedRanges = new ArrayList<>();

    public InitializedFormat(
            FormatId formatId,
            int durationMs,
            int endTimeMs,
            String mimeType,
            String videoId,
            FormatSelector formatSelector,
            int totalSegments,
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
