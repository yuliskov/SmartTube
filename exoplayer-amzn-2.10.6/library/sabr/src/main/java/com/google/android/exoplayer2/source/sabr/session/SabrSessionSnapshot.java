package com.google.android.exoplayer2.source.sabr.session;

import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamProtectionStatus;

/** Immutable, redacted session snapshot suitable for diagnostics UI. */
public final class SabrSessionSnapshot {
    public final SabrSessionCoordinator.State state;
    public final long generation;
    public final long lastRequestNumber;
    public final boolean cookiePresent;
    public final int activeContextCount;
    public final int unsentContextCount;
    public final int reloadCount;
    public final int redirectCount;
    public final StreamProtectionStatus.Status protectionStatus;
    public final SabrLiveWindowState liveWindow;

    SabrSessionSnapshot(
            SabrSessionCoordinator.State state,
            long generation,
            long lastRequestNumber,
            boolean cookiePresent,
            int activeContextCount,
            int unsentContextCount,
            int reloadCount,
            int redirectCount,
            StreamProtectionStatus.Status protectionStatus,
            SabrLiveWindowState liveWindow) {
        this.state = state;
        this.generation = generation;
        this.lastRequestNumber = lastRequestNumber;
        this.cookiePresent = cookiePresent;
        this.activeContextCount = activeContextCount;
        this.unsentContextCount = unsentContextCount;
        this.reloadCount = reloadCount;
        this.redirectCount = redirectCount;
        this.protectionStatus = protectionStatus;
        this.liveWindow = liveWindow;
    }
}
