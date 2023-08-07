package com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;

public interface PlayerEngineEventListener {
    /**
     * The error occurred loading data
     */
    int ERROR_TYPE_SOURCE = ExoPlaybackException.TYPE_SOURCE;
    /**
     * The error occurred in a renderer.
     */
    int ERROR_TYPE_RENDERER = ExoPlaybackException.TYPE_RENDERER;
    /**
     * The error was an unexpected
     */
    int ERROR_TYPE_UNEXPECTED = ExoPlaybackException.TYPE_UNEXPECTED;
    /**
     * The error occurred in a remote component.
     */
    int ERROR_TYPE_REMOTE = ExoPlaybackException.TYPE_REMOTE;
    /**
     * The error was an {@link OutOfMemoryError}.
     */
    int ERROR_TYPE_OUT_OF_MEMORY = ExoPlaybackException.TYPE_OUT_OF_MEMORY;
    void onPlay();
    void onPause();
    void onPlayEnd();
    void onBuffering();
    void onSeekEnd();
    void onSourceChanged(Video item);
    void onVideoLoaded(Video item);
    void onEngineInitialized();
    void onEngineReleased();
    void onEngineError(int type, String message);
    void onTrackChanged(FormatItem track);
    void onTrackSelected(FormatItem track);
}
