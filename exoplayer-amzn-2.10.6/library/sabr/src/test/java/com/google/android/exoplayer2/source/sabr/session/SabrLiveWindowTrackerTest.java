package com.google.android.exoplayer2.source.sabr.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.android.exoplayer2.source.sabr.protos.videostreaming.LiveMetadata;

import org.junit.Test;

public class SabrLiveWindowTrackerTest {
    @Test
    public void convertsTicksAndClampsWindowToLiveHead() {
        FakeClock clock = new FakeClock(1234);
        SabrLiveWindowTracker tracker = new SabrLiveWindowTracker(clock, 30_000);

        tracker.update(LiveMetadata.newBuilder()
                .setSource("broadcast-safe-label")
                .setHeadSequenceNumber(88)
                .setHeadSequenceTimeMs(120_000)
                .setWallTimeMs(1_700_000_000_000L)
                .setMinSeekableTimeTicks(45_000)
                .setMinSeekableTimescale(1_000)
                .setMaxSeekableTimeTicks(150_000)
                .setMaxSeekableTimescale(1_000)
                .build(), false, 7);

        SabrLiveWindowState state = tracker.snapshot();
        assertTrue(state.isSeekable());
        assertTrue(state.isDynamic());
        assertEquals(45_000, state.getWindowStartMs());
        assertEquals(120_000, state.getWindowEndMs());
        assertEquals(120_000, state.getLiveHeadMs());
        assertEquals(88, state.getLiveHeadSequence());
        assertEquals(90_000, state.getGoLiveTargetMs());
        assertEquals(1234, state.getLastUpdateElapsedRealtimeMs());
        assertEquals(7, state.getSourceGeneration());
    }

    @Test
    public void clampsUserSeekAndDetectsBehindLiveWindow() {
        SabrLiveWindowTracker tracker = populatedTracker(false);

        assertEquals(45_000, tracker.clampSeekPositionMs(0));
        assertEquals(80_000, tracker.clampSeekPositionMs(80_000));
        assertEquals(120_000, tracker.clampSeekPositionMs(999_000));
        assertTrue(tracker.isBehindLiveWindow(44_999));
        assertFalse(tracker.isBehindLiveWindow(45_000));
    }

    @Test
    public void postLiveTransitionStopsDynamicEdge() {
        SabrLiveWindowTracker tracker = populatedTracker(false);

        tracker.setPostLive(true, 8);

        SabrLiveWindowState state = tracker.snapshot();
        assertTrue(state.isPostLive());
        assertFalse(state.isDynamic());
        assertEquals(120_000, state.getWindowEndMs());
        assertEquals(8, state.getSourceGeneration());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroTimescale() {
        SabrLiveWindowTracker.ticksToMs(1, 0);
    }

    @Test
    public void avoidsOverflowDuringTickConversion() {
        assertEquals(Long.MAX_VALUE / 1000,
                SabrLiveWindowTracker.ticksToMs(Long.MAX_VALUE / 1000, 1000));
    }

    @Test
    public void metadataWithoutHeadAndMinimumDoesNotInventSeekableWindow() {
        SabrLiveWindowTracker tracker = new SabrLiveWindowTracker(new FakeClock(0), 30_000);
        tracker.update(LiveMetadata.newBuilder().setWallTimeMs(1).build(), false, 1);

        assertFalse(tracker.snapshot().isSeekable());
        assertFalse(tracker.snapshot().isDynamic());
    }

    private static SabrLiveWindowTracker populatedTracker(boolean postLive) {
        SabrLiveWindowTracker tracker = new SabrLiveWindowTracker(new FakeClock(0), 30_000);
        tracker.update(LiveMetadata.newBuilder()
                .setHeadSequenceNumber(88)
                .setHeadSequenceTimeMs(120_000)
                .setMinSeekableTimeTicks(45_000)
                .setMinSeekableTimescale(1_000)
                .setMaxSeekableTimeTicks(120_000)
                .setMaxSeekableTimescale(1_000)
                .build(), postLive, 7);
        return tracker;
    }

    private static final class FakeClock implements SabrLiveWindowTracker.Clock {
        private final long nowMs;

        private FakeClock(long nowMs) {
            this.nowMs = nowMs;
        }

        @Override
        public long elapsedRealtimeMs() {
            return nowMs;
        }
    }
}
