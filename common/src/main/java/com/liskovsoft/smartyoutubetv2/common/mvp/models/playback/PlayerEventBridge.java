package com.liskovsoft.smartyoutubetv2.common.mvp.models.playback;

public interface PlayerEventBridge extends PlayerEventListener {
    void setController(PlayerController controller);
}
