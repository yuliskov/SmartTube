package com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

public interface PlayerEventListener extends PlayerUiEventListener, PlayerEngineEventListener, ViewEventListener {
    void openVideo(Video item);
    void onInitDone();
    void onFinish();
}
