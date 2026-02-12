package com.google.android.exoplayer2.source.sabr.parser.frames;

import com.google.android.exoplayer2.extractor.TrackOutput;

public class VorbisFrameExtractor extends BaseFrameExtractor {
    public VorbisFrameExtractor(TrackOutput trackOutput, long startTimeUs, long frameDurationUs) {
        super(trackOutput, startTimeUs, frameDurationUs);
    }

    @Override
    protected int findNextFrameStart(int from) {
        // Vorbis is stored in Ogg pages, each page starts with "OggS"
        for (int i = from; i < buffer.limit() - 3; i++) {
            if (buffer.data[i] == 'O' && buffer.data[i+1] == 'g' &&
                    buffer.data[i+2] == 'g' && buffer.data[i+3] == 'S') {
                return i;
            }
        }
        return -1;
    }
}
