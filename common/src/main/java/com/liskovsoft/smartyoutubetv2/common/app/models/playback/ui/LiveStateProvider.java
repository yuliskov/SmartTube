package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

/**
 * Provides the state of a live stream that the player timeline doesn't have (e.g. sabr).
 */
public interface LiveStateProvider {
    /**
     * @return live stream length in ms or -1 if unknown
     */
    long getLiveDurationMs();

    /**
     * The player timeline may be longer than what YouTube lets to rewind (e.g. absolute stream time).
     * @return start of the seekable window in the player timeline or 0 if the whole timeline is seekable
     */
    long getLiveStartMs();

    /**
     * @return true if the stream can't be rewound (live without DVR)
     */
    boolean isSeekBlocked();
}
