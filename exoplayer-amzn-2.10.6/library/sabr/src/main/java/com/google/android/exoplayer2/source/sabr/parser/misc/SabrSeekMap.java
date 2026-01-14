package com.google.android.exoplayer2.source.sabr.parser.misc;

import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.SeekPoint;
import com.google.android.exoplayer2.util.Util;

public final class SabrSeekMap implements SeekMap {
    private final long durationUs;
    private final long[] segmentStartTimesUs;

    /**
     * @param totalSegments number of segments
     * @param segmentDurationMs duration of each segment in milliseconds
     * @param totalDurationMs total duration of the video in milliseconds
     */
    public SabrSeekMap(int totalSegments, long segmentDurationMs, long totalDurationMs) {
        this.durationUs = totalDurationMs * 1000L; // convert to µs
        this.segmentStartTimesUs = new long[totalSegments];
        for (int i = 0; i < totalSegments; i++) {
            this.segmentStartTimesUs[i] = i * segmentDurationMs * 1000L; // convert to µs
        }
    }

    @Override
    public boolean isSeekable() {
        return true;
    }

    @Override
    public long getDurationUs() {
        return durationUs;
    }

    /**
     * Accepts time in microseconds (ExoPlayer always calls in µs), but you could
     * convert from ms if calling externally.
     */
    @Override
    public SeekPoints getSeekPoints(long timeUs) {
        // Clamp time to duration
        if (timeUs > durationUs) timeUs = durationUs;

        // Find the segment index
        int index = Util.binarySearchFloor(
                segmentStartTimesUs,
                timeUs,
                /* inclusive */ true,
                /* stayInBounds */ true
        );

        // Use segment index as position
        return new SeekPoints(
                new SeekPoint(segmentStartTimesUs[index], index)
        );
    }

    /** Optional helper to get SeekPoints from milliseconds (ms) */
    public SeekPoints getSeekPointsFromMs(long timeMs) {
        return getSeekPoints(timeMs * 1000L);
    }
}
