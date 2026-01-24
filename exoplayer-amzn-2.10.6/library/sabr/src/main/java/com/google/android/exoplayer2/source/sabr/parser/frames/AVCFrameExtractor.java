package com.google.android.exoplayer2.source.sabr.parser.frames;

import com.google.android.exoplayer2.extractor.TrackOutput;

public class AVCFrameExtractor extends BaseFrameExtractor {
    public AVCFrameExtractor(TrackOutput trackOutput, long startTimeUs, long frameDurationUs) {
        super(trackOutput, startTimeUs, frameDurationUs);
    }

    @Override
    protected int findNextFrameStart(int from) {
        // Look for NAL unit start code: 0x000001 or 0x00000001
        for (int i = from; i < buffer.limit() - 3; i++) {
            if (buffer.data[i] == 0x00 && buffer.data[i+1] == 0x00) {
                if (buffer.data[i+2] == 0x01) return i;
                if (i + 3 < buffer.limit() && buffer.data[i+2] == 0x00 && buffer.data[i+3] == 0x01) return i;
            }
        }
        return -1;
    }
}
