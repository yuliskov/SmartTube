package com.google.android.exoplayer2.source.sabr.parser.processor;

import com.google.android.exoplayer2.source.sabr.parser.parts.MediaSegmentEndSabrPart;

public class ProcessMediaEndResult {
    public MediaSegmentEndSabrPart sabrPart;
    public boolean isNewSegment; // TODO: better name
}
