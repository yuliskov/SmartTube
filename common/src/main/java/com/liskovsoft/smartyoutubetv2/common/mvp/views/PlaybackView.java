package com.liskovsoft.smartyoutubetv2.common.mvp.views;

import com.liskovsoft.smartyoutubetv2.common.mvp.models.playback.PlayerCommandProcessor;

public interface PlaybackView {
    void setPlayerProcessor(PlayerCommandProcessor stateBridge);
}
