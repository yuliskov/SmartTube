package com.liskovsoft.smartyoutubetv2.common.app.views;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;

public interface PlaybackView {
    void setEventListener(PlayerEventListener stateBridge);
    PlayerEventListener getEventListener();
    PlayerManager getPlayer();
    void showProgressBar(boolean show);
}
