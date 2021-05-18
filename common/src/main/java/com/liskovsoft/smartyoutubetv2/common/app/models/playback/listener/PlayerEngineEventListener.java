package com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;

public interface PlayerEngineEventListener {
    /**
     * The error occurred loading data
     */
    int ERROR_TYPE_SOURCE = 0;
    /**
     * The error occurred in a renderer.
     */
    int ERROR_TYPE_RENDERER = 1;
    /**
     * The error was an unexpected
     */
    int ERROR_TYPE_UNEXPECTED = 2;
    /**
     * The error occurred in a remote component.
     */
    int ERROR_TYPE_REMOTE = 3;
    /**
     * The error was an {@link OutOfMemoryError}.
     */
    int ERROR_TYPE_OUT_OF_MEMORY = 4;
    void onPlay();
    void onPause();
    void onPlayEnd();
    void onBuffering();
    void onSeek();
    void onSourceChanged(Video item);
    void onVideoLoaded(Video item);
    void onEngineInitialized();
    void onEngineReleased();
    void onEngineError(int type);
    void onTrackChanged(FormatItem track);
}
