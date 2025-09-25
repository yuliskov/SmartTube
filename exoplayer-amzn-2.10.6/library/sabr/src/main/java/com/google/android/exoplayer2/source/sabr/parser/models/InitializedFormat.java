package com.google.android.exoplayer2.source.sabr.parser.models;

import com.google.android.exoplayer2.source.sabr.protos.videostreaming.FormatId;

import java.util.List;

public class InitializedFormat {
    public FormatId formatId;
    public int sequenceLmt = -1;
    public boolean discard;
    public List<ConsumedRange> consumedRanges;
    public Segment currentSegment;
    public Segment initSegment;
    public FormatSelector formatSelector;
    public long totalSegments = -1;
}
