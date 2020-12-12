package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import android.content.Context;
import androidx.annotation.NonNull;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.AutoFrameRateHelper;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.ModeSyncManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

import java.util.ArrayList;
import java.util.List;

public class AutoFrameRateManager extends PlayerEventListenerHelper {
    private static final String TAG = AutoFrameRateManager.class.getSimpleName();
    private static final int AUTO_FRAME_RATE_ID = 21;
    private final HqDialogManager mUiManager;
    //private FormatItem mSelectedVideoTrack;
    private final AutoFrameRateHelper mAutoFrameRateHelper;
    private final ModeSyncManager mModeSyncManager;
    private final Runnable mApplyAfr = this::applyAfr;
    private OptionCategory mCategory;
    private PlayerData mPlayerData;

    public static class AfrData {
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
    public void onInitDone() {
        mPlayerData = PlayerData.instance(getActivity());
        AfrData afrData = mPlayerData.getAfrData();
        initUiOptions();
        mAutoFrameRateHelper.saveOriginalState(getActivity());
        mAutoFrameRateHelper.setFpsCorrectionEnabled(afrData.afrFpsCorrectionEnabled);
        mAutoFrameRateHelper.setResolutionSwitchEnabled(afrData.afrResSwitchEnabled, false);
    }

    @Override
    public void onViewResumed() {
        addUiOptions();
    }

    @Override
    public void onVideoLoaded(Video item) {
        applyAfr();
    }

    private void onFpsCorrectionClick() {
        mAutoFrameRateHelper.setFpsCorrectionEnabled(mPlayerData.getAfrData().afrFpsCorrectionEnabled);
    }

    private void onResolutionSwitchClick() {
        AfrData afrData = mPlayerData.getAfrData();

        boolean force = afrData.afrEnabled;
        mAutoFrameRateHelper.setResolutionSwitchEnabled(afrData.afrResSwitchEnabled, force);
    }

    private void applyAfrWrapper() {
        AfrData afrData = mPlayerData.getAfrData();

        if (afrData.afrEnabled) {
            AppSettingsPresenter.instance(getActivity()).showDialogMessage("Applying AFR...", this::applyAfr, 1_000);
        }
    }

    private void applyAfr() {
        AfrData afrData = mPlayerData.getAfrData();

        if (afrData.afrEnabled) {
            applyAfr(getController().getVideoFormat(), false);
        } else {
            restoreAfr();
        }
    }

    private void restoreAfr() {
        String msg = "Restoring original frame rate...";
        Log.d(TAG, msg);
        mAutoFrameRateHelper.restoreOriginalState(getActivity());
        mModeSyncManager.save(null);
    }

    private void applyAfr(FormatItem track, boolean force) {
        if (track != null) {
            String msg = String.format("Applying afr... fps: %s, resolution: %sx%s, activity: %s",
                    track.getFrameRate(), track.getWidth(), track.getHeight(), getActivity().getClass().getSimpleName());
            Log.d(TAG, msg);
            mAutoFrameRateHelper.apply(getActivity(), track, force);
            //mModeSyncManager.save(track);
        }
    }

    private void initUiOptions() {
        mCategory = createAutoFrameRateCategory(
                getActivity(), PlayerData.instance(getActivity()), () -> {}, this::onResolutionSwitchClick, this::onFpsCorrectionClick);
    }

    private void addUiOptions() {
        if (mCategory == null) {
            return;
        }

        if (mAutoFrameRateHelper.isSupported()) {
            mUiManager.addCheckedCategory(mCategory);
            mUiManager.addOnDialogHide(mApplyAfr);
        } else {
            mUiManager.removeCategory(AUTO_FRAME_RATE_ID);
            mUiManager.removeOnDialogHide(mApplyAfr);
        }
    }

    public static OptionCategory createAutoFrameRateCategory(Context context, PlayerData playerData) {
        return createAutoFrameRateCategory(context, playerData, () -> {}, () -> {}, () -> {});
    }

    public static OptionCategory createAutoFrameRateCategory(Context context, PlayerData playerData, Runnable onAfrCallback, Runnable onResolutionCallback, Runnable onFpsCorrectionCallback) {
        String title = context.getString(R.string.auto_frame_rate);
        String fpsCorrection = context.getString(R.string.frame_rate_correction, "30->29.97, 60->59.94");
        String resolutionSwitch = context.getString(R.string.resolution_switch);
        List<OptionItem> options = new ArrayList<>();
        AfrData afrData = playerData.getAfrData();

        OptionItem afrEnableOption = UiOptionItem.from(title, optionItem -> {
            afrData.afrEnabled = optionItem.isSelected();
            playerData.setAfrData(afrData);
            onAfrCallback.run();
        }, afrData.afrEnabled);
        OptionItem afrResSwitchOption = UiOptionItem.from(resolutionSwitch, optionItem -> {
            afrData.afrResSwitchEnabled = optionItem.isSelected();
            playerData.setAfrData(afrData);
            onResolutionCallback.run();
        }, afrData.afrResSwitchEnabled);
        OptionItem afrFpsCorrectionOption = UiOptionItem.from(fpsCorrection, optionItem -> {
            afrData.afrFpsCorrectionEnabled = optionItem.isSelected();
            playerData.setAfrData(afrData);
            onFpsCorrectionCallback.run();
        }, afrData.afrFpsCorrectionEnabled);

        afrResSwitchOption.setRequire(afrEnableOption);
        afrFpsCorrectionOption.setRequire(afrEnableOption);

        options.add(afrEnableOption);
        options.add(afrResSwitchOption);
        options.add(afrFpsCorrectionOption);

        return OptionCategory.from(AUTO_FRAME_RATE_ID, title, options);
    }
}
