package com.liskovsoft.smartyoutubetv2.common.app.views;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListener;

public interface PlaybackView {
    void setEventListener(PlayerEventListener stateBridge);
    PlayerController getController();
}
