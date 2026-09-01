package com.google.android.exoplayer2.source.sabr.session;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.source.sabr.protos.videostreaming.LiveMetadata;

import java.util.ArrayList;
import java.util.List;

/** Thread-safe owner of the current live window. */
public final class SabrLiveWindowTracker {
    public interface Clock {
        long elapsedRealtimeMs();
    }

    public interface Listener {
        void onLiveWindowChanged(SabrLiveWindowState state);
    }

    private final Clock clock;
    private final long targetLatencyMs;
    private final List<Listener> listeners = new ArrayList<>();
    private SabrLiveWindowState state = SabrLiveWindowState.empty();
    private @Nullable String broadcastId;

    public SabrLiveWindowTracker(Clock clock, long targetLatencyMs) {
        if (targetLatencyMs < 0) {
            throw new IllegalArgumentException("targetLatencyMs must be non-negative");
        }
        this.clock = clock;
        this.targetLatencyMs = targetLatencyMs;
    }

    public synchronized SabrLiveWindowState snapshot() {
        return state;
    }

    public void update(LiveMetadata metadata, boolean postLive, long generation) {
        long headMs = metadata.hasHeadSequenceTimeMs()
                ? metadata.getHeadSequenceTimeMs() : SabrLiveWindowState.UNSET;
        long startMs = convertOptional(
                metadata.hasMinSeekableTimeTicks(), metadata.getMinSeekableTimeTicks(),
                metadata.hasMinSeekableTimescale(), metadata.getMinSeekableTimescale());
        long maxMs = convertOptional(
                metadata.hasMaxSeekableTimeTicks(), metadata.getMaxSeekableTimeTicks(),
                metadata.hasMaxSeekableTimescale(), metadata.getMaxSeekableTimescale());

        boolean seekable = headMs >= 0 && startMs >= 0;
        long endMs = SabrLiveWindowState.UNSET;
        long goLiveMs = SabrLiveWindowState.UNSET;
        if (seekable) {
            endMs = maxMs >= 0 ? Math.min(maxMs, headMs) : headMs;
            startMs = Math.min(startMs, endMs);
            goLiveMs = clamp(saturatingSubtract(headMs, targetLatencyMs), startMs, endMs);
        } else {
            startMs = SabrLiveWindowState.UNSET;
        }

        SabrLiveWindowState next = new SabrLiveWindowState(
                broadcastId,
                metadata.hasSource() ? metadata.getSource() : null,
                headMs,
                metadata.hasHeadSequenceNumber() ? metadata.getHeadSequenceNumber() : -1,
                startMs,
                endMs,
                metadata.hasWallTimeMs() ? metadata.getWallTimeMs() : SabrLiveWindowState.UNSET,
                goLiveMs,
                seekable,
                postLive,
                clock.elapsedRealtimeMs(),
                generation);
        publish(next);
    }

    public void setPostLive(boolean postLive, long generation) {
        SabrLiveWindowState current = snapshot();
        if (!current.isSeekable()) {
            return;
        }
        publish(new SabrLiveWindowState(
                current.getBroadcastId(), current.getSource(), current.getLiveHeadMs(),
                current.getLiveHeadSequence(), current.getWindowStartMs(), current.getWindowEndMs(),
                current.getWallTimeMs(), current.getGoLiveTargetMs(), current.isSeekable(), postLive,
                clock.elapsedRealtimeMs(), generation));
    }

    public synchronized void setBroadcastId(@Nullable String broadcastId) {
        this.broadcastId = broadcastId;
    }

    public long clampSeekPositionMs(long requestedPositionMs) {
        SabrLiveWindowState current = snapshot();
        return current.isSeekable()
                ? clamp(requestedPositionMs, current.getWindowStartMs(), current.getWindowEndMs())
                : requestedPositionMs;
    }

    public long getGoLiveTargetMs() {
        return snapshot().getGoLiveTargetMs();
    }

    public boolean isBehindLiveWindow(long positionMs) {
        SabrLiveWindowState current = snapshot();
        return current.isSeekable() && positionMs < current.getWindowStartMs();
    }

    public synchronized void addListener(Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public synchronized void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void publish(SabrLiveWindowState next) {
        List<Listener> snapshotListeners;
        synchronized (this) {
            if (state.materiallyEquals(next)) {
                state = next;
                return;
            }
            state = next;
            snapshotListeners = new ArrayList<>(listeners);
        }
        for (Listener listener : snapshotListeners) {
            listener.onLiveWindowChanged(next);
        }
    }

    public static long ticksToMs(long ticks, int timescale) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must be non-negative");
        }
        if (timescale <= 0) {
            throw new IllegalArgumentException("timescale must be positive");
        }
        long whole = ticks / timescale;
        long remainder = ticks % timescale;
        if (whole > Long.MAX_VALUE / 1000) {
            throw new IllegalArgumentException("tick conversion overflows milliseconds");
        }
        long wholeMs = whole * 1000;
        long remainderMs = (remainder * 1000) / timescale;
        return wholeMs + remainderMs;
    }

    private static long convertOptional(
            boolean hasTicks, long ticks, boolean hasTimescale, int timescale) {
        if (!hasTicks || !hasTimescale) {
            return SabrLiveWindowState.UNSET;
        }
        try {
            return ticksToMs(ticks, timescale);
        } catch (IllegalArgumentException invalidMetadata) {
            return SabrLiveWindowState.UNSET;
        }
    }

    private static long saturatingSubtract(long value, long amount) {
        return value < amount ? 0 : value - amount;
    }

    private static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }
}
