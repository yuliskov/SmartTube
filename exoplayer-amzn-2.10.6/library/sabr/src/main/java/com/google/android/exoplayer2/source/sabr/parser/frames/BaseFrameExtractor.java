package com.google.android.exoplayer2.source.sabr.parser.frames;

import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.C;
import java.io.IOException;

/**
 * Base class for frame extractors of various media formats.
 * Subclasses implement findNextFrameStart() for their format.
 */
public abstract class BaseFrameExtractor {
    protected final ParsableByteArray buffer = new ParsableByteArray(32 * 1024);
    private final TrackOutput trackOutput;
    private long timeUs;
    private final long frameDurationUs;
    private boolean firstFrameInSegment = true;

    public BaseFrameExtractor(TrackOutput trackOutput, long startTimeUs, long frameDurationUs) {
        this.trackOutput = trackOutput;
        this.timeUs = startTimeUs;
        this.frameDurationUs = frameDurationUs;
    }

    /**
     * Read data from ExtractorInput and emit complete frames zero-copy.
     */
    public void readFromInput(ExtractorInput input, int maxLength) throws IOException, InterruptedException {
        int bytesRead = input.read(buffer.data, buffer.limit(), maxLength);
        if (bytesRead <= 0) return;

        buffer.setLimit(buffer.limit() + bytesRead);

        int frameStart;
        while ((frameStart = findNextFrameStart(0)) >= 0) {
            int frameEnd = findNextFrameStart(frameStart + 1);

            // If no next frame start is found, send the remaining buffer as the last frame
            if (frameEnd < 0) frameEnd = buffer.limit();

            int frameSize = frameEnd - frameStart;
            if (frameSize <= 0) break;

            int flags = firstFrameInSegment ? C.BUFFER_FLAG_KEY_FRAME : 0;
            firstFrameInSegment = false;

            // Zero-copy: use buffer directly
            buffer.setPosition(frameStart);
            buffer.setLimit(frameEnd);
            trackOutput.sampleData(buffer, frameSize);
            trackOutput.sampleMetadata(timeUs, flags, frameSize, 0, null);

            timeUs += frameDurationUs;

            // Shift remaining data to the beginning of the buffer
            int remaining = buffer.limit() - frameEnd;
            if (remaining > 0) {
                System.arraycopy(buffer.data, frameEnd, buffer.data, 0, remaining);
            }
            buffer.setPosition(0);
            buffer.setLimit(remaining);
        }
    }

    /**
     * Reset state for a new segment. The first frame will be flagged as keyframe.
     */
    public void resetForNewSegment() {
        firstFrameInSegment = true;
    }

    /**
     * Subclasses implement this to find the next frame start in the buffer.
     *
     * @param from index to start searching
     * @return index of the next frame start or -1 if not found
     */
    protected abstract int findNextFrameStart(int from);
}