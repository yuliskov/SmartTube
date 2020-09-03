package com.liskovsoft.smartyoutubetv2.common.mvp.views;

import com.liskovsoft.smartyoutubetv2.common.mvp.models.playback.PlayerController;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.playback.PlayerEventListener;

public interface PlaybackView {
    void setListener(PlayerEventListener stateBridge);
    PlayerController getController();
}
