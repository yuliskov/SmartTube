package com.google.android.exoplayer2.source.sabr.parser.frames;

import com.google.android.exoplayer2.extractor.TrackOutput;

public class VP9FrameExtractor extends BaseFrameExtractor {
    public VP9FrameExtractor(TrackOutput trackOutput, long startTimeUs, long frameDurationUs) {
        super(trackOutput, startTimeUs, frameDurationUs);
    }

    @Override
    protected int findNextFrameStart(int from) {
        for (int i = from; i < buffer.limit(); i++) {
            int b = buffer.data[i] & 0xFF;
            if ((b & 0xC0) == 0x80) return i; // VP9 frame_marker
        }
        return -1;
    }
}
