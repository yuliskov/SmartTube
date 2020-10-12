package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import android.app.Activity;
import androidx.annotation.NonNull;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.AutoFrameRateHelper;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.ModeSyncManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;

import java.util.ArrayList;
import java.util.List;

public class AutoFrameRateManager extends PlayerEventListenerHelper {
    private static final String TAG = AutoFrameRateManager.class.getSimpleName();
    private final HqDialogManager mUiManager;
    private boolean mMainActivityRunOnce;
    private FormatItem mSelectedVideoTrack;
    private final AutoFrameRateHelper mAutoFrameRateHelper;
    private final ModeSyncManager mModeSyncManager;
    private AfrData mAfrData = new AfrData();

    private static class AfrData {
        public boolean afrEnabled;
        public boolean afrFpsCorrectionEnabled;
        public boolean afrResSwitchEnabled;

        public AfrData() {
        }

        public AfrData(boolean afrEnabled, boolean afrFpsCorrectionEnabled, boolean afrResSwitchEnabled) {
            this.afrEnabled = afrEnabled;
            this.afrFpsCorrectionEnabled = afrFpsCorrectionEnabled;
            this.afrResSwitchEnabled = afrResSwitchEnabled;
        }

        @NonNull
        public String toString() {
            return String.format("%s,%s,%s", afrEnabled, afrFpsCorrectionEnabled, afrResSwitchEnabled);
        }

        public static AfrData from(String data) {
            if (data == null) {
                return new AfrData();
            }

            String[] split = data.split(",");

            if (split.length < 3) {
                return new AfrData();
            }

            return new AfrData(
                    Boolean.parseBoolean(split[0]), Boolean.parseBoolean(split[1]), Boolean.parseBoolean(split[2]));
        }
    }

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
            restoreAfrData();

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

    private void persistAfrData() {
        AppPrefs.instance(mActivity).setAfrData(mAfrData.toString());
    }

    private void restoreAfrData() {
        mAfrData = AfrData.from(AppPrefs.instance(mActivity).getAfrData(null));
    }

    private void onAfrOptionClick(OptionItem optionItem) {
        mAfrData.afrEnabled = optionItem.isSelected();
        persistAfrData();
    }

    private void onFpsCorrectionClick(OptionItem optionItem) {
        mAfrData.afrFpsCorrectionEnabled = optionItem.isSelected();
        mAutoFrameRateHelper.setFpsCorrectionEnabled(mAfrData.afrFpsCorrectionEnabled);
        persistAfrData();
    }

    private void onResolutionSwitchClick(OptionItem optionItem) {
        mAfrData.afrResSwitchEnabled = optionItem.isSelected();
        mAutoFrameRateHelper.setResolutionSwitchEnabled(mAfrData.afrResSwitchEnabled);
        persistAfrData();
    }

    private void applyAfr() {
        if (mAfrData.afrEnabled) {
            applyAfr(mSelectedVideoTrack, false);
        } else {
            restoreAfr();
        }
    }

    private void forceApplyAfr() {
        if (mAfrData.afrEnabled) {
            applyAfr(mSelectedVideoTrack, true);
        } else {
            restoreAfr();
        }
    }

    private void restoreAfr() {
        Log.d(TAG, "Restoring afr...");
        mAutoFrameRateHelper.restoreOriginalState(mActivity);
        mModeSyncManager.save(null);
    }

    private void applyAfr(FormatItem track, boolean force) {
        if (track != null) {
            Log.d(TAG, "Applying afr: " + track.getFrameRate());
            mAutoFrameRateHelper.apply(track, mActivity, force);
            mModeSyncManager.save(track);
        }
    }

    private void addUiOptions() {
        String title = mActivity.getString(R.string.auto_frame_rate);
        String fpsCorrection = mActivity.getString(R.string.frame_rate_correction, "30->29.97, 60->59.94");
        String resolutionSwitch = mActivity.getString(R.string.resolution_switch);
        List<OptionItem> options = new ArrayList<>();

        OptionItem afrEnableOption = UiOptionItem.from(title, this::onAfrOptionClick, mAfrData.afrEnabled);
        OptionItem afrResSwitchOption = UiOptionItem.from(resolutionSwitch, this::onResolutionSwitchClick, mAfrData.afrResSwitchEnabled);
        OptionItem afrFpsCorrectionOption = UiOptionItem.from(fpsCorrection, this::onFpsCorrectionClick, mAfrData.afrFpsCorrectionEnabled);

        afrResSwitchOption.setRequire(afrEnableOption);
        afrFpsCorrectionOption.setRequire(afrEnableOption);

        options.add(afrEnableOption);
        options.add(afrResSwitchOption);
        options.add(afrFpsCorrectionOption);

        mUiManager.addCheckedCategory(title, options);

        mUiManager.setOnDialogHide(this::forceApplyAfr);
    }
}
