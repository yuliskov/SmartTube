package com.google.android.exoplayer2.source.sabr.parser.frames;

import com.google.android.exoplayer2.extractor.ExtractorInput;
import java.io.IOException;

public interface FrameExtractor {
    /**
     * Read data from ExtractorInput into the buffer.
     * Emit complete frames to TrackOutput immediately if possible.
     *
     * @param input     ExtractorInput to read from
     * @param maxLength maximum number of bytes to read
     */
    void readFromInput(ExtractorInput input, int maxLength) throws IOException;

    /**
     * Reset state for a new segment (e.g., first frame is keyframe)
     */
    void resetForNewSegment();
}
