package com.google.android.exoplayer2.source.sabr.session;

import androidx.annotation.Nullable;

/** Immutable live-edge and DVR-window snapshot derived only from SABR metadata. */
public final class SabrLiveWindowState {
    public static final long UNSET = -1;

    private final @Nullable String broadcastId;
    private final @Nullable String source;
    private final long liveHeadMs;
    private final int liveHeadSequence;
    private final long windowStartMs;
    private final long windowEndMs;
    private final long wallTimeMs;
    private final long goLiveTargetMs;
    private final boolean seekable;
    private final boolean postLive;
    private final long lastUpdateElapsedRealtimeMs;
    private final long sourceGeneration;

    SabrLiveWindowState(
            @Nullable String broadcastId,
            @Nullable String source,
            long liveHeadMs,
            int liveHeadSequence,
            long windowStartMs,
            long windowEndMs,
            long wallTimeMs,
            long goLiveTargetMs,
            boolean seekable,
            boolean postLive,
            long lastUpdateElapsedRealtimeMs,
            long sourceGeneration) {
        this.broadcastId = broadcastId;
        this.source = source;
        this.liveHeadMs = liveHeadMs;
        this.liveHeadSequence = liveHeadSequence;
        this.windowStartMs = windowStartMs;
        this.windowEndMs = windowEndMs;
        this.wallTimeMs = wallTimeMs;
        this.goLiveTargetMs = goLiveTargetMs;
        this.seekable = seekable;
        this.postLive = postLive;
        this.lastUpdateElapsedRealtimeMs = lastUpdateElapsedRealtimeMs;
        this.sourceGeneration = sourceGeneration;
    }

    static SabrLiveWindowState empty() {
        return new SabrLiveWindowState(
                null, null, UNSET, -1, UNSET, UNSET, UNSET, UNSET,
                false, false, UNSET, 0);
    }

    public @Nullable String getBroadcastId() { return broadcastId; }
    public @Nullable String getSource() { return source; }
    public long getLiveHeadMs() { return liveHeadMs; }
    public int getLiveHeadSequence() { return liveHeadSequence; }
    public long getWindowStartMs() { return windowStartMs; }
    public long getWindowEndMs() { return windowEndMs; }
    public long getWallTimeMs() { return wallTimeMs; }
    public long getGoLiveTargetMs() { return goLiveTargetMs; }
    public boolean isSeekable() { return seekable; }
    public boolean isPostLive() { return postLive; }
    public boolean isDynamic() { return seekable && !postLive; }
    public long getLastUpdateElapsedRealtimeMs() { return lastUpdateElapsedRealtimeMs; }
    public long getSourceGeneration() { return sourceGeneration; }

    boolean materiallyEquals(SabrLiveWindowState other) {
        return equalsNullable(broadcastId, other.broadcastId)
                && equalsNullable(source, other.source)
                && liveHeadMs == other.liveHeadMs
                && liveHeadSequence == other.liveHeadSequence
                && windowStartMs == other.windowStartMs
                && windowEndMs == other.windowEndMs
                && wallTimeMs == other.wallTimeMs
                && goLiveTargetMs == other.goLiveTargetMs
                && seekable == other.seekable
                && postLive == other.postLive
                && sourceGeneration == other.sourceGeneration;
    }

    private static boolean equalsNullable(Object first, Object second) {
        return first == second || (first != null && first.equals(second));
    }
}
