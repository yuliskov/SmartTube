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
    private boolean mEnabled;
    private boolean mCorrectionEnabled;
    private boolean mMainActivityRunOnce;
    private boolean mParentActivityRunOnce;
    private FormatItem mSelectedVideoTrack;
    private AutoFrameRateHelper mParentAutoFrameRateHelper;
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

    @Override
    public void onParentActivity(Activity activity) {
        super.onParentActivity(activity);

        if (!mParentActivityRunOnce) {
            mParentAutoFrameRateHelper = new AutoFrameRateHelper(mParentActivity);

            mParentActivityRunOnce = true;
        } else {
            mParentAutoFrameRateHelper.setActivity(mParentActivity);
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
        mModeSyncManager.save(null);
    }

    private void applyAfr(FormatItem track) {
        mAutoFrameRateHelper.apply(track);
        mParentAutoFrameRateHelper.apply(track);
        mModeSyncManager.save(track);
    }

    private void addUiOptions() {
        String title = mActivity.getString(R.string.auto_frame_rate_enable);
        String fpsCorrection = mActivity.getString(R.string.auto_frame_rate_correction, "30->29.97, 60->59.94");
        List<OptionItem> options = new ArrayList<>();
        options.add(UiOptionItem.from(title, this::onAfrOptionClick, mEnabled));
        options.add(UiOptionItem.from(fpsCorrection, this::onFpsCorrectionClick, mCorrectionEnabled));

        mUiManager.addCheckedCategory(title, options);
    }
}
