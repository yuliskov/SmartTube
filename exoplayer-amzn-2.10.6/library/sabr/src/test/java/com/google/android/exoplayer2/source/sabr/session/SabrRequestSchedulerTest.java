package com.google.android.exoplayer2.source.sabr.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.android.exoplayer2.source.sabr.protos.videostreaming.NextRequestPolicy;

import org.junit.Test;

public class SabrRequestSchedulerTest {
    @Test
    public void backoffPreventsBusyRequestLoopAndUsesMonotonicClock() {
        FakeClock clock = new FakeClock();
        SabrRequestScheduler scheduler = new SabrRequestScheduler(clock);
        scheduler.onRequestStarted();
        scheduler.onPolicy(NextRequestPolicy.newBuilder().setBackoffTimeMs(5000).build());

        assertFalse(scheduler.shouldRequest(0, 0, true, true));
        assertEquals(5000, scheduler.millisUntilRequestAllowed(0, 0, true, true));
        clock.advance(4999);
        assertFalse(scheduler.shouldRequest(0, 0, true, true));
        clock.advance(1);
        assertTrue(scheduler.shouldRequest(0, 0, true, true));
    }

    @Test
    public void combinesTargetMinimumAndMaximumAgePolicy() {
        FakeClock clock = new FakeClock();
        SabrRequestScheduler scheduler = new SabrRequestScheduler(clock);
        scheduler.onPolicy(NextRequestPolicy.newBuilder()
                .setTargetAudioReadaheadMs(10_000)
                .setTargetVideoReadaheadMs(8_000)
                .setMinAudioReadaheadMs(2_000)
                .setMinVideoReadaheadMs(1_500)
                .setMaxTimeSinceLastRequestMs(4_000)
                .build());
        scheduler.onRequestStarted();

        assertFalse(scheduler.shouldRequest(10_000, 8_000, true, true));
        assertTrue(scheduler.shouldRequest(1_999, 8_000, true, true));
        assertTrue(scheduler.shouldRequest(10_000, 1_499, true, true));
        clock.advance(4_000);
        assertTrue(scheduler.shouldRequest(10_000, 8_000, true, true));
    }

    @Test
    public void closeCancelsFutureScheduling() {
        SabrRequestScheduler scheduler = new SabrRequestScheduler(new FakeClock());
        scheduler.close();

        assertFalse(scheduler.shouldRequest(0, 0, true, true));
        assertEquals(Long.MAX_VALUE, scheduler.millisUntilRequestAllowed(0, 0, true, true));
    }

    private static final class FakeClock implements SabrRequestScheduler.Clock {
        private long nowMs;

        @Override
        public long elapsedRealtimeMs() {
            return nowMs;
        }

        void advance(long durationMs) {
            nowMs += durationMs;
        }
    }
}
