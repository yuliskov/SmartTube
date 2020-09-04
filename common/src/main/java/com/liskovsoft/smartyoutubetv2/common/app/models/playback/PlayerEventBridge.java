package com.liskovsoft.smartyoutubetv2.common.app.models.playback;

public interface PlayerEventBridge extends PlayerEventListener {
    void setController(PlayerController controller);
}
