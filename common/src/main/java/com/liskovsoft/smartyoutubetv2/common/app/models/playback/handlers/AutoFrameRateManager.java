package com.liskovsoft.smartyoutubetv2.common.app.models.playback.handlers;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlayerController;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.AutoFrameRateHelper;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;

public class AutoFrameRateManager extends PlayerEventListenerHelper {
    private AutoFrameRateHelper mAutoFrameRateHelper;

    @Override
    public void setController(PlayerController controller) {
        super.setController(controller);
        mAutoFrameRateHelper = new AutoFrameRateHelper(mActivity);
    }

    @Override
    public void onVideoTrackChanged(FormatItem track) {
        if (track.getType() == FormatItem.TYPE_VIDEO) {
            mAutoFrameRateHelper.apply(track);
        }
    }
}
