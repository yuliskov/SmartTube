package com.liskovsoft.smartyoutubetv2.common.app.views;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;

public interface PlaybackView {
    void setEventListener(PlayerEventListener stateBridge);
    PlayerEventListener getEventListener();
    PlaybackController getController();
    void showProgressBar(boolean show);
}
