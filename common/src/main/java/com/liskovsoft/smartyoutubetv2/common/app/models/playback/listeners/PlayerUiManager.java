package com.liskovsoft.smartyoutubetv2.common.app.models.playback.listeners;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;

public class PlayerUiManager extends PlayerEventListenerHelper {
    @Override
    public void onPlayEnd() {
        mController.showControls(false);
    }
}
