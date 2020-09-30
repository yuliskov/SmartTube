package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import android.app.Activity;
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
    private final HqDialogManager mUiManager;
    private AutoFrameRateHelper mAutoFrameRateHelper;
    private boolean mAfrEnabled;
    private boolean mCorrectionEnabled;
    private boolean mSwitchEnabled;
    private boolean mMainActivityRunOnce;
    //private boolean mParentActivityRunOnce;
    private FormatItem mSelectedVideoTrack;
    //private AutoFrameRateHelper mParentAutoFrameRateHelper;
    private ModeSyncManager mModeSyncManager;

    public AutoFrameRateManager(HqDialogManager uiManager) {
        mUiManager = uiManager;
    }

    @Override
    public void onActivity(Activity activity) {
        super.onActivity(activity);

        if (!mMainActivityRunOnce) {
            mAutoFrameRateHelper = new AutoFrameRateHelper(mActivity);
            mModeSyncManager = ModeSyncManager.instance(activity);

            addUiOptions();

            mMainActivityRunOnce = true;
        } else {
            mAutoFrameRateHelper.setActivity(mActivity);
        }
    }

    //@Override
    //public void onParentActivity(Activity activity) {
    //    super.onParentActivity(activity);
    //
    //    if (!mParentActivityRunOnce) {
    //        mParentAutoFrameRateHelper = new AutoFrameRateHelper(mParentActivity);
    //
    //        mParentActivityRunOnce = true;
    //    } else {
    //        mParentAutoFrameRateHelper.setActivity(mParentActivity);
    //    }
    //}

    @Override
    public void onTrackChanged(FormatItem track) {
        if (track.getType() == FormatItem.TYPE_VIDEO) {
            mSelectedVideoTrack = track;

            if (mAfrEnabled) {
                applyAfr(track);
            }
        }
    }

    private void onAfrOptionClick(OptionItem optionItem) {
        mAfrEnabled = optionItem.isSelected();

        if (mAfrEnabled) {
            applyAfr(mSelectedVideoTrack);
        } else {
            restoreAfr();
        }
    }

    private void onFpsCorrectionClick(OptionItem optionItem) {
        mCorrectionEnabled = optionItem.isSelected();
        mAutoFrameRateHelper.setFpsCorrectionEnabled(mCorrectionEnabled);
    }

    private void onResolutionSwitchClick(OptionItem optionItem) {
        mSwitchEnabled = optionItem.isSelected();
        mAutoFrameRateHelper.setResolutionSwitchEnabled(mSwitchEnabled);
        applyAfr(mSelectedVideoTrack);
    }

    private void restoreAfr() {
        mAutoFrameRateHelper.restoreOriginalState();
        //mParentAutoFrameRateHelper.restoreOriginalState();
        mModeSyncManager.save(null);
    }

    private void applyAfr(FormatItem track) {
        if (track != null) {
            mAutoFrameRateHelper.apply(track);
            //mParentAutoFrameRateHelper.apply(track);
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
    }
}
