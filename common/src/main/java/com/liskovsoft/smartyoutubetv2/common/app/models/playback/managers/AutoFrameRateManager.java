package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.AutoFrameRateHelper;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;

public class AutoFrameRateManager extends PlayerEventListenerHelper {
    private final PlayerUiManager mUiManager;
    private AutoFrameRateHelper mAutoFrameRateHelper;
    private boolean mEnabled;
    private boolean mCorrectionEnabled;
    private boolean mRunOnce;
    private FormatItem mSelectedVideoTrack;
    private AutoFrameRateHelper mParentAutoFrameRateHelper;

    public AutoFrameRateManager(PlayerUiManager uiManager) {
        mUiManager = uiManager;
    }

    @Override
    public void setController(PlaybackController controller) {
        super.setController(controller);

        if (!mRunOnce) {
            mAutoFrameRateHelper = new AutoFrameRateHelper(mActivity);
            mParentAutoFrameRateHelper = new AutoFrameRateHelper(mParentActivity);

            String title = mActivity.getString(R.string.auto_frame_rate_enable);
            String fpsCorrection = mActivity.getString(R.string.auto_frame_rate_correction, "(30 => 29.97)");
            mUiManager.addHQSwitch(title,
                    UiOptionItem.from(title, this::onAfrOptionClick, mEnabled));
            mUiManager.addHQSwitch(title,
                    UiOptionItem.from(fpsCorrection, this::onFpsCorrectionClick, mCorrectionEnabled)
            );

            mRunOnce = true;
        } else {
            mAutoFrameRateHelper.updateActivity(mActivity);
            mParentAutoFrameRateHelper.updateActivity(mParentActivity);
        }
    }

    @Override
    public void onTrackChanged(FormatItem track) {
        if (track.getType() == FormatItem.TYPE_VIDEO) {
            mSelectedVideoTrack = track;

            if (mEnabled) {
                applyAfr(track);
            }
        }
    }

    private void onAfrOptionClick(OptionItem optionItem) {
        mEnabled = optionItem.isSelected();

        if (mEnabled) {
            applyAfr(mSelectedVideoTrack);
        } else {
            restoreAfr();
        }
    }

    private void onFpsCorrectionClick(OptionItem optionItem) {
        mCorrectionEnabled = optionItem.isSelected();
        mAutoFrameRateHelper.setFpsCorrectionEnabled(mCorrectionEnabled);
    }

    private void restoreAfr() {
        mAutoFrameRateHelper.restoreOriginalState();
        mParentAutoFrameRateHelper.restoreOriginalState();
    }

    private void applyAfr(FormatItem track) {
        mAutoFrameRateHelper.apply(track);
        mParentAutoFrameRateHelper.apply(track);
    }
}
