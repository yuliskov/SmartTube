package com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackController;

public interface PlayerEventListener extends PlayerUiEventListener, PlayerEngineEventListener, ViewEventListener {
    void openVideo(Video item);
    /**
     * Called after creation of {@link PlaybackController}
     */
    void onInitDone();
    void onFinish();
}
