package com.google.android.exoplayer2.source.sabr.session;

import com.google.android.exoplayer2.source.sabr.protos.videostreaming.NextRequestPolicy;

/** Non-blocking, cancellation-aware request gate driven by a monotonic clock. */
public final class SabrRequestScheduler {
    public interface Clock {
        long elapsedRealtimeMs();
    }

    private final Clock clock;
    private NextRequestPolicy policy = NextRequestPolicy.getDefaultInstance();
    private long lastRequestElapsedMs = -1;
    private long backoffDeadlineElapsedMs;
    private boolean closed;

    public SabrRequestScheduler(Clock clock) {
        this.clock = clock;
    }

    public synchronized void onRequestStarted() {
        if (!closed) {
            lastRequestElapsedMs = clock.elapsedRealtimeMs();
        }
    }

    public synchronized void onPolicy(NextRequestPolicy policy) {
        if (closed) {
            return;
        }
        this.policy = policy;
        if (policy.hasBackoffTimeMs() && policy.getBackoffTimeMs() > 0) {
            long now = clock.elapsedRealtimeMs();
            long requestedDeadline = saturatingAdd(now, policy.getBackoffTimeMs());
            backoffDeadlineElapsedMs = Math.max(backoffDeadlineElapsedMs, requestedDeadline);
        }
    }

    public synchronized boolean shouldRequest(
            long audioBufferedMs, long videoBufferedMs, boolean audioEnabled, boolean videoEnabled) {
        return millisUntilRequestAllowed(
                audioBufferedMs, videoBufferedMs, audioEnabled, videoEnabled) == 0;
    }

    public synchronized long millisUntilRequestAllowed(
            long audioBufferedMs, long videoBufferedMs, boolean audioEnabled, boolean videoEnabled) {
        if (closed) {
            return Long.MAX_VALUE;
        }
        long now = clock.elapsedRealtimeMs();
        if (now < backoffDeadlineElapsedMs) {
            return backoffDeadlineElapsedMs - now;
        }
        if (lastRequestElapsedMs < 0) {
            return 0;
        }
        if (!policy.hasTargetAudioReadaheadMs() && !policy.hasTargetVideoReadaheadMs()
                && !policy.hasMinAudioReadaheadMs() && !policy.hasMinVideoReadaheadMs()
                && !policy.hasMaxTimeSinceLastRequestMs()) {
            return 0;
        }

        long ageMs = now - lastRequestElapsedMs;
        if (policy.hasMaxTimeSinceLastRequestMs()
                && ageMs >= policy.getMaxTimeSinceLastRequestMs()) {
            return 0;
        }
        if (belowMinimum(audioBufferedMs, audioEnabled,
                policy.hasMinAudioReadaheadMs(), policy.getMinAudioReadaheadMs())
                || belowMinimum(videoBufferedMs, videoEnabled,
                policy.hasMinVideoReadaheadMs(), policy.getMinVideoReadaheadMs())) {
            return 0;
        }

        boolean audioAtTarget = atTarget(audioBufferedMs, audioEnabled,
                policy.hasTargetAudioReadaheadMs(), policy.getTargetAudioReadaheadMs());
        boolean videoAtTarget = atTarget(videoBufferedMs, videoEnabled,
                policy.hasTargetVideoReadaheadMs(), policy.getTargetVideoReadaheadMs());
        if (!audioAtTarget || !videoAtTarget) {
            return 0;
        }

        if (policy.hasMaxTimeSinceLastRequestMs()) {
            return Math.max(1, policy.getMaxTimeSinceLastRequestMs() - ageMs);
        }
        return Long.MAX_VALUE;
    }

    public synchronized long getBackoffDeadlineElapsedMs() {
        return backoffDeadlineElapsedMs;
    }

    public synchronized NextRequestPolicy getPolicy() {
        return policy;
    }

    public synchronized void resetForGeneration() {
        lastRequestElapsedMs = -1;
        backoffDeadlineElapsedMs = 0;
    }

    public synchronized void close() {
        closed = true;
    }

    private static boolean belowMinimum(
            long bufferedMs, boolean enabled, boolean hasMinimum, int minimumMs) {
        return enabled && hasMinimum && bufferedMs < minimumMs;
    }

    private static boolean atTarget(
            long bufferedMs, boolean enabled, boolean hasTarget, int targetMs) {
        return !enabled || !hasTarget || bufferedMs >= targetMs;
    }

    private static long saturatingAdd(long value, long amount) {
        return value > Long.MAX_VALUE - amount ? Long.MAX_VALUE : value + amount;
    }
}
