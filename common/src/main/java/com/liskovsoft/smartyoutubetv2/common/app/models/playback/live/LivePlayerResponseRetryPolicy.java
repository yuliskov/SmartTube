package com.liskovsoft.smartyoutubetv2.common.app.models.playback.live;

/**
 * Bounds fresh player-response attempts for one logical live video.
 * A retired response can trigger at most one client rotation, and stale errors cannot retire a
 * newer response.
 */
public final class LivePlayerResponseRetryPolicy {
    private final int maxPlayerResponses;
    private int playerResponseCount;
    private long activeGeneration = -1;
    private boolean activeGenerationRetired;

    public LivePlayerResponseRetryPolicy(int maxPlayerResponses) {
        if (maxPlayerResponses < 1) {
            throw new IllegalArgumentException("maxPlayerResponses must be positive");
        }
        this.maxPlayerResponses = maxPlayerResponses;
    }

    public void onPlayerResponse(long generation) {
        if (generation == activeGeneration && playerResponseCount > 0) {
            return;
        }
        activeGeneration = generation;
        activeGenerationRetired = false;
        playerResponseCount++;
    }

    public boolean tryRetireForbiddenGeneration(long generation) {
        if (generation != activeGeneration || activeGenerationRetired ||
                playerResponseCount >= maxPlayerResponses) {
            return false;
        }
        activeGenerationRetired = true;
        return true;
    }

    public void reset() {
        playerResponseCount = 0;
        activeGeneration = -1;
        activeGenerationRetired = false;
    }

    public int getPlayerResponseCount() {
        return playerResponseCount;
    }
}
