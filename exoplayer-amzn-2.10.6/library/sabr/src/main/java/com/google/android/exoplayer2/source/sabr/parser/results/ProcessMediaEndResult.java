package com.google.android.exoplayer2.source.sabr.parser.results;

import com.google.android.exoplayer2.source.sabr.parser.parts.MediaSegmentEndSabrPart;

public class ProcessMediaEndResult {
    public MediaSegmentEndSabrPart sabrPart;
    public boolean isNewSegment; // TODO: better name
}
