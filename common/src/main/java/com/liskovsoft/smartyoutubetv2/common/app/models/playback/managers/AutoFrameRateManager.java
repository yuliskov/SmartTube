package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import android.app.Activity;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.AutoFrameRateHelper;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.ModeSyncManager;

import java.util.ArrayList;
import java.util.List;

public class AutoFrameRateManager extends PlayerEventListenerHelper {
    private static final String TAG = AutoFrameRateManager.class.getSimpleName();
    private final HqDialogManager mUiManager;
    private boolean mAfrEnabled;
    private boolean mCorrectionEnabled;
    private boolean mSwitchEnabled;
    private boolean mMainActivityRunOnce;
    private FormatItem mSelectedVideoTrack;
    private final AutoFrameRateHelper mAutoFrameRateHelper;
    private final ModeSyncManager mModeSyncManager;

    public AutoFrameRateManager(HqDialogManager uiManager) {
        mUiManager = uiManager;
        mAutoFrameRateHelper = new AutoFrameRateHelper();
        mModeSyncManager = ModeSyncManager.instance();
        mModeSyncManager.setAfrHelper(mAutoFrameRateHelper);
    }

    @Override
    public void onActivity(Activity activity) {
        super.onActivity(activity);

        if (!mMainActivityRunOnce) {
            addUiOptions();
            mAutoFrameRateHelper.saveOriginalState(activity);
            mMainActivityRunOnce = true;
        }
    }

    @Override
    public void onTrackChanged(FormatItem track) {
        if (track.getType() == FormatItem.TYPE_VIDEO) {
            mSelectedVideoTrack = track;

            applyAfr();
        }
    }

    private void onAfrOptionClick(OptionItem optionItem) {
        mAfrEnabled = optionItem.isSelected();
    }

    private void onFpsCorrectionClick(OptionItem optionItem) {
        mCorrectionEnabled = optionItem.isSelected();
        mAutoFrameRateHelper.setFpsCorrectionEnabled(mCorrectionEnabled);
    }

    private void onResolutionSwitchClick(OptionItem optionItem) {
        mSwitchEnabled = optionItem.isSelected();
        mAutoFrameRateHelper.setResolutionSwitchEnabled(mSwitchEnabled);
    }

    private void applyAfr() {
        if (mAfrEnabled) {
            applyAfr(mSelectedVideoTrack);
        } else {
            restoreAfr();
        }
    }

    private void restoreAfr() {
        Log.d(TAG, "Restoring afr...");
        mAutoFrameRateHelper.restoreOriginalState(mActivity);
        mModeSyncManager.save(null);
    }

    private void applyAfr(FormatItem track) {
        if (track != null) {
            Log.d(TAG, "Applying afr: " + track.getFrameRate());
            mAutoFrameRateHelper.apply(track, mActivity);
            mModeSyncManager.save(track);
        }
    }

    private void addUiOptions() {
        String title = mActivity.getString(R.string.auto_frame_rate);
        String fpsCorrection = mActivity.getString(R.string.frame_rate_correction, "30->29.97, 60->59.94");
        String resolutionSwitch = mActivity.getString(R.string.resolution_switch);
        List<OptionItem> options = new ArrayList<>();
        options.add(UiOptionItem.from(title, this::onAfrOptionClick, mAfrEnabled));
        options.add(UiOptionItem.from(resolutionSwitch, this::onResolutionSwitchClick, mSwitchEnabled));
        options.add(UiOptionItem.from(fpsCorrection, this::onFpsCorrectionClick, mCorrectionEnabled));

        mUiManager.addCheckedCategory(title, options);

        mUiManager.setOnDialogHide(this::applyAfr);
    }
}
