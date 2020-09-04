package com.liskovsoft.smartyoutubetv2.common.app.models.playback.listeners;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;

public class PositionRestorer extends PlayerEventListenerHelper {
    private PlayerController mController;
    private long mPositionMs;

    public PositionRestorer(PlayerController controller) {
        mController = controller;
    }

    @Override
    public void onViewDestroyed() {
        mPositionMs = 0;
    }

    @Override
    public void onViewPaused() {
        mPositionMs = mController.getPositionMs();
    }

    @Override
    public void onViewResumed() {
        if (mPositionMs != 0) {
            mController.setPosition(mPositionMs);
        }
    }
}
