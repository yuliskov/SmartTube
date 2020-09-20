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
    private boolean mInitDone;
    private FormatItem mSelectedVideoTrack;

    public AutoFrameRateManager(PlayerUiManager uiManager) {
        mUiManager = uiManager;
    }

    @Override
    public void setController(PlayerController controller) {
        super.setController(controller);

        if (!mInitDone) {
            mAutoFrameRateHelper = new AutoFrameRateHelper(mActivity);

            String title = mActivity.getString(R.string.auto_frame_rate_enable);
            String fpsCorrection = mActivity.getString(R.string.auto_frame_rate_correction, "(30 => 29.97)");
            mUiManager.addHQSwitch(title, UiOptionItem.from(title,
                    (optionItem) -> {
                        mEnabled = optionItem.isSelected();

                        if (mEnabled) {
                            mAutoFrameRateHelper.apply(mSelectedVideoTrack);
                        } else {
                            mAutoFrameRateHelper.restoreOriginalState();
                        }
                    }, mEnabled));
            mUiManager.addHQSwitch(title, UiOptionItem.from(fpsCorrection,
                    (optionItem) -> {
                        mCorrectionEnabled = optionItem.isSelected();

                        mAutoFrameRateHelper.setFpsCorrectionEnabled(mCorrectionEnabled);
                    }, mCorrectionEnabled));

            mInitDone = true;
        } else {
            mAutoFrameRateHelper.updateActivity(mActivity);
        }
    }

    @Override
    public void onTrackChanged(FormatItem track) {
        if (track.getType() == FormatItem.TYPE_VIDEO) {
            mSelectedVideoTrack = track;

            if (mEnabled) {
                mAutoFrameRateHelper.apply(track);
            }
        }
    }
}
