package com.google.android.exoplayer2.source.sabr.parser.frames;

import com.google.android.exoplayer2.extractor.TrackOutput;

public class AACFrameExtractor extends BaseFrameExtractor {
    public AACFrameExtractor(TrackOutput trackOutput, long startTimeUs, long frameDurationUs) {
        super(trackOutput, startTimeUs, frameDurationUs);
    }

    @Override
    protected int findNextFrameStart(int from) {
        // Look for ADTS syncword: 0xFFF (first 12 bits)
        for (int i = from; i < buffer.limit() - 1; i++) {
            int header = ((buffer.data[i] & 0xFF) << 4) | ((buffer.data[i+1] & 0xF0) >> 4);
            if (header == 0xFFF) return i;
        }
        return -1;
    }
}
