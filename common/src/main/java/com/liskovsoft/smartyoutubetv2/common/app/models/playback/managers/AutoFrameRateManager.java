package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlayerController;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.AutoFrameRateHelper;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;

public class AutoFrameRateManager extends PlayerEventListenerHelper {
    private final PlayerUiManager mUiManager;
    private AutoFrameRateHelper mAutoFrameRateHelper;
    private boolean mEnabled;

    public AutoFrameRateManager(PlayerUiManager uiManager) {
        mUiManager = uiManager;
    }

    @Override
    public void setController(PlayerController controller) {
        super.setController(controller);
        mAutoFrameRateHelper = new AutoFrameRateHelper(mActivity);
        mUiManager.addHQSwitch(mActivity.getString(R.string.enable_auto_frame_rate), (checked) -> mEnabled = checked);

    }

    @Override
    public void onTrackChanged(FormatItem track) {
        if (!mEnabled) {
            return;
        }

        if (track.getType() == FormatItem.TYPE_VIDEO) {
            mAutoFrameRateHelper.apply(track);
        }
    }
}
