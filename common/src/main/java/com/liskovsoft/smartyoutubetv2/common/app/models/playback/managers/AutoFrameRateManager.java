package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.AutoFrameRateHelper;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.ModeSyncManager;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.internal.DisplayHolder.Mode;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.internal.DisplaySyncHelper.AutoFrameRateListener;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.internal.UhdHelper;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

import java.util.ArrayList;
import java.util.List;

public class AutoFrameRateManager extends PlayerEventListenerHelper implements AutoFrameRateListener {
    private static final String TAG = AutoFrameRateManager.class.getSimpleName();
    private static final int AUTO_FRAME_RATE_ID = 21;
    private static final int AUTO_FRAME_RATE_DELAY_ID = 22;
    private final HQDialogManager mUiManager;
    private StateUpdater mStateUpdater;
    private final AutoFrameRateHelper mAutoFrameRateHelper;
    private final ModeSyncManager mModeSyncManager;
    private final Runnable mApplyAfr = this::applyAfr;
    private final Handler mHandler;
    private PlayerData mPlayerData;
    private final Runnable mPlaybackResumeHandler = () -> {
        if (mStateUpdater != null) {
            mStateUpdater.blockPlay(false);
        }
        getController().setPlay(true);
    };

    public AutoFrameRateManager(HQDialogManager uiManager, StateUpdater stateUpdater) {
        mUiManager = uiManager;
        mStateUpdater = stateUpdater;
        mAutoFrameRateHelper = new AutoFrameRateHelper();
        mAutoFrameRateHelper.setListener(this);
        mModeSyncManager = ModeSyncManager.instance();
        mModeSyncManager.setAfrHelper(mAutoFrameRateHelper);
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onInitDone() {
        mPlayerData = PlayerData.instance(getActivity());
        mAutoFrameRateHelper.saveOriginalState(getActivity());
    }

    @Override
    public void onViewResumed() {
        mAutoFrameRateHelper.setFpsCorrectionEnabled(mPlayerData.isAfrFpsCorrectionEnabled());
        mAutoFrameRateHelper.setResolutionSwitchEnabled(mPlayerData.isAfrResSwitchEnabled(), false);

        addUiOptions();
    }

    @Override
    public void onVideoLoaded(Video item) {
        applyAfr();
    }

    @Override
    public void onModeStart(Mode newMode) {
        // Ugoos already displays this message on each mode switch
        String message = getActivity().getString(
                R.string.auto_frame_rate_applying,
                newMode.getPhysicalWidth(),
                newMode.getPhysicalHeight(),
                newMode.getRefreshRate());
        Log.d(TAG, message);
        //MessageHelpers.showLongMessage(getActivity(), message);
        pausePlayback();
    }

    @Override
    public void onModeError(Mode newMode) {
        String msg = getActivity().getString(R.string.msg_mode_switch_error, UhdHelper.toResolution(newMode));
        Log.e(TAG, msg);
        MessageHelpers.showMessage(getActivity(), msg);
        //applyAfr();
    }

    private void onFpsCorrectionClick() {
        mAutoFrameRateHelper.setFpsCorrectionEnabled(mPlayerData.isAfrFpsCorrectionEnabled());
    }

    private void onResolutionSwitchClick() {
        mAutoFrameRateHelper.setResolutionSwitchEnabled(mPlayerData.isAfrResSwitchEnabled(), mPlayerData.isAfrEnabled());
    }

    private void applyAfrWrapper() {
        if (mPlayerData.isAfrEnabled()) {
            AppDialogPresenter.instance(getActivity()).showDialogMessage("Applying AFR...", this::applyAfr, 1_000);
        }
    }

    private void applyAfr() {
        if (mPlayerData.isAfrEnabled()) {
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

    private void pausePlayback() {
        mHandler.removeCallbacks(mPlaybackResumeHandler);
        mStateUpdater.blockPlay(false);

        if (mPlayerData.getAfrPauseSec() > 0) {
            mStateUpdater.blockPlay(true);
            getController().setPlay(false);
            mHandler.postDelayed(mPlaybackResumeHandler, mPlayerData.getAfrPauseSec() * 1_000);
        }
    }

    private void addUiOptions() {
        if (mAutoFrameRateHelper.isSupported()) {
            OptionCategory afrCategory = createAutoFrameRateCategory(
                    getActivity(), PlayerData.instance(getActivity()),
                    () -> {}, this::onResolutionSwitchClick, this::onFpsCorrectionClick);

            OptionCategory afrDelayCategory = createAutoFrameRatePauseCategory(
                    getActivity(), PlayerData.instance(getActivity()));

            mUiManager.addCategory(afrCategory);
            mUiManager.addCategory(afrDelayCategory);
            mUiManager.addOnDialogHide(mApplyAfr);
        } else {
            mUiManager.removeCategory(AUTO_FRAME_RATE_ID);
            mUiManager.removeCategory(AUTO_FRAME_RATE_DELAY_ID);
            mUiManager.removeOnDialogHide(mApplyAfr);
        }
    }

    public static OptionCategory createAutoFrameRateCategory(Context context, PlayerData playerData) {
        return createAutoFrameRateCategory(context, playerData, () -> {}, () -> {}, () -> {});
    }

    private static OptionCategory createAutoFrameRateCategory(Context context, PlayerData playerData, Runnable onAfrCallback, Runnable onResolutionCallback, Runnable onFpsCorrectionCallback) {
        String title = context.getString(R.string.auto_frame_rate);
        String fpsCorrection = context.getString(R.string.frame_rate_correction, "24->23.97, 30->29.97, 60->59.94");
        String resolutionSwitch = context.getString(R.string.resolution_switch);
        List<OptionItem> options = new ArrayList<>();

        OptionItem afrEnableOption = UiOptionItem.from(title, optionItem -> {
            playerData.setAfrEnabled(optionItem.isSelected());
            onAfrCallback.run();
        }, playerData.isAfrEnabled());
        OptionItem afrResSwitchOption = UiOptionItem.from(resolutionSwitch, optionItem -> {
            playerData.setAfrResSwitchEnabled(optionItem.isSelected());
            onResolutionCallback.run();
        }, playerData.isAfrResSwitchEnabled());
        OptionItem afrFpsCorrectionOption = UiOptionItem.from(fpsCorrection, optionItem -> {
            playerData.setAfrFpsCorrectionEnabled(optionItem.isSelected());
            onFpsCorrectionCallback.run();
        }, playerData.isAfrFpsCorrectionEnabled());

        afrResSwitchOption.setRequire(afrEnableOption);
        afrFpsCorrectionOption.setRequire(afrEnableOption);

        options.add(afrEnableOption);
        options.add(afrResSwitchOption);
        options.add(afrFpsCorrectionOption);

        return OptionCategory.from(AUTO_FRAME_RATE_ID, OptionCategory.TYPE_CHECKED, title, options);
    }

    public static OptionCategory createAutoFrameRatePauseCategory(Context context, PlayerData playerData) {
        String title = context.getString(R.string.auto_frame_rate_pause);

        List<OptionItem> options = new ArrayList<>();

        for (int pauseSec : new int[] {0, 1, 2, 3, 4, 5, 6, 7}) {
            String optionTitle = pauseSec == 0 ? context.getString(R.string.option_never) : String.format("%s sec", pauseSec);
            options.add(UiOptionItem.from(optionTitle,
                    optionItem -> {
                        playerData.setAfrPauseSec(pauseSec);
                        playerData.setAfrEnabled(true);
                    },
                    pauseSec == playerData.getAfrPauseSec()));
        }

        return OptionCategory.from(AUTO_FRAME_RATE_DELAY_ID, OptionCategory.TYPE_RADIO, title, options);
    }
}
