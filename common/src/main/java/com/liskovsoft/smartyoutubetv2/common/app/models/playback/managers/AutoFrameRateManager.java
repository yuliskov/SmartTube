package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import android.app.Activity;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.AutoFrameRateHelper;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;

public class AutoFrameRateManager extends PlayerEventListenerHelper {
    private final PlayerUiManager mUiManager;
    private AutoFrameRateHelper mAutoFrameRateHelper;
    private boolean mEnabled;
    private boolean mCorrectionEnabled;
    private boolean mMainActivityRunOnce;
    private boolean mParentActivityRunOnce;
    private FormatItem mSelectedVideoTrack;
    private AutoFrameRateHelper mParentAutoFrameRateHelper;

    public AutoFrameRateManager(PlayerUiManager uiManager) {
        mUiManager = uiManager;
    }

    @Override
    public void onMainActivity(Activity activity) {
        super.onMainActivity(activity);

        if (!mMainActivityRunOnce) {
            mAutoFrameRateHelper = new AutoFrameRateHelper(mMainActivity);

            addUiOptions();

            mMainActivityRunOnce = true;
        } else {
            mAutoFrameRateHelper.updateActivity(mMainActivity);
        }
    }

    @Override
    public void onParentActivity(Activity activity) {
        super.onParentActivity(activity);

        if (!mParentActivityRunOnce) {
            mParentAutoFrameRateHelper = new AutoFrameRateHelper(mParentActivity);

            mParentActivityRunOnce = true;
        } else {
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

    private void addUiOptions() {
        String title = mMainActivity.getString(R.string.auto_frame_rate_enable);
        String fpsCorrection = mMainActivity.getString(R.string.auto_frame_rate_correction, "30->29.97, 60->59.94");
        mUiManager.addHQSwitch(title,
                UiOptionItem.from(title, this::onAfrOptionClick, mEnabled));
        mUiManager.addHQSwitch(title,
                UiOptionItem.from(fpsCorrection, this::onFpsCorrectionClick, mCorrectionEnabled)
        );
    }
}
