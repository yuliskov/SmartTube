package com.liskovsoft.smartyoutubetv2.common.app.models.playback.listeners;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;

public class PositionRestorer extends PlayerEventListenerHelper {
    private long mPositionMs;

    @Override
    public void onVideoLoaded() {
        if (mPositionMs != 0) {
            mController.setPositionMs(mPositionMs);
        }
    }

    @Override
    public void onViewDestroyed() {
        mPositionMs = 0;
    }

    @Override
    public void onViewPaused() {
        mPositionMs = mController.getPositionMs();
    }
}
