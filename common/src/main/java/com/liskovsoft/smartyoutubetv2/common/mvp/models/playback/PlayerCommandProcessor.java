package com.liskovsoft.smartyoutubetv2.common.mvp.models.playback;

public interface PlayerCommandProcessor extends PlayerEventListener {
    void setCommandHandler(PlayerCommandHandler commandHandler);
}
