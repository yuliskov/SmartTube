package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.AutoFrameRateHelper;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;

public class AutoFrameRateManager extends PlayerEventListenerHelper {
    private final PlayerUiManager mUiManager;
    private AutoFrameRateHelper mAutoFrameRateHelper;
    private boolean mEnabled;
    private boolean mCorrectionEnabled;

    public AutoFrameRateManager(PlayerUiManager uiManager) {
        mUiManager = uiManager;
    }

    @Override
    public void setController(PlayerController controller) {
        super.setController(controller);
        mAutoFrameRateHelper = new AutoFrameRateHelper(mActivity);
        String title = mActivity.getString(R.string.auto_frame_rate_enable);
        String fpsCorrection = mActivity.getString(R.string.auto_frame_rate_correction);
        mUiManager.addHQSwitch(title, UiOptionItem.from(title,
                (optionItem) -> mEnabled = optionItem.isSelected(), mEnabled));
        mUiManager.addHQSwitch(title, UiOptionItem.from(fpsCorrection,
                (optionItem) -> mCorrectionEnabled = optionItem.isSelected(), mCorrectionEnabled));
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
