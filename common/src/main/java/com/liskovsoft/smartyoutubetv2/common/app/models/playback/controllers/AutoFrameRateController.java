package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.AutoFrameRateHelper;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.ModeSyncManager;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.internal.DisplayHolder.Mode;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.internal.DisplaySyncHelper.AutoFrameRateListener;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.internal.UhdHelper;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.utils.TvQuickActions;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AutoFrameRateController extends PlayerEventListenerHelper implements AutoFrameRateListener {
    private static final String TAG = AutoFrameRateController.class.getSimpleName();
    private static final int AUTO_FRAME_RATE_ID = 21;
    private static final int AUTO_FRAME_RATE_DELAY_ID = 22;
    private static final int AUTO_FRAME_RATE_MODES_ID = 23;
    private static final long SHORTS_DURATION_MS = 30 * 1_000;
    private final HQDialogController mHQController;
    private final VideoStateController mStateUpdater;
    private final AutoFrameRateHelper mAutoFrameRateHelper;
    private final ModeSyncManager mModeSyncManager;
    private final Runnable mApplyAfr = this::applyAfr;
    private final Runnable mApplyAfrStop = this::applyAfrStop;
    private PlayerData mPlayerData;
    private boolean mIsPlay;
    private final Runnable mPlaybackResumeHandler = () -> {
        if (getPlayer() != null) {
            restorePlayback();
        }
    };

    public AutoFrameRateController(HQDialogController hqController, VideoStateController stateUpdater) {
        mHQController = hqController;
        mStateUpdater = stateUpdater;
        mAutoFrameRateHelper = AutoFrameRateHelper.instance(null);
        mAutoFrameRateHelper.setListener(this);
        mModeSyncManager = ModeSyncManager.instance();
        mModeSyncManager.setAfrHelper(mAutoFrameRateHelper);
    }

    @Override
    public void onInit() {
        mPlayerData = PlayerData.instance(getContext());
        mAutoFrameRateHelper.saveOriginalState(getActivity());
    }

    @Override
    public void onViewResumed() {
        mAutoFrameRateHelper.setFpsCorrectionEnabled(mPlayerData.isAfrFpsCorrectionEnabled());
        mAutoFrameRateHelper.setResolutionSwitchEnabled(mPlayerData.isAfrResSwitchEnabled(), false);
        mAutoFrameRateHelper.setDoubleRefreshRateEnabled(mPlayerData.isDoubleRefreshRateEnabled());
        mAutoFrameRateHelper.setSkip24RateEnabled(mPlayerData.isSkip24RateEnabled());

        addUiOptions();
    }

    @Override
    public void onVideoLoaded(Video item) {
        savePlayback();

        // Sometimes AFR is not working on activity startup. Trying to fix with delay.
        applyAfrDelayed();
        //applyAfr();
    }

    @Override
    public void onModeStart(Mode newMode) {
        if (getContext() == null) {
            return;
        }

        // Ugoos already displays this message on each mode switch
        @SuppressLint("StringFormatMatches")
        String message = getContext().getString(
                R.string.auto_frame_rate_applying,
                newMode.getPhysicalWidth(),
                newMode.getPhysicalHeight(),
                newMode.getRefreshRate());
        Log.d(TAG, message);
        //MessageHelpers.showLongMessage(getActivity(), message);
        maybePausePlayback();
    }

    @Override
    public void onModeError(Mode newMode) {
        if (getContext() == null) {
            return;
        }

        if (newMode != null) {
            String msg = getContext().getString(R.string.msg_mode_switch_error, UhdHelper.toResolution(newMode));
            Log.e(TAG, msg);

            restorePlayback();

            // This error could appear even on success.
            // MessageHelpers.showMessage(getActivity(), msg);
        } else {
            // Seems that the device doesn't support direct mode switching.
            // Use tvQuickActions instead.
            maybePausePlayback();
        }
    }

    @Override
    public void onCancel() {
        restorePlayback();
    }

    @Override
    public void onEngineReleased() {
        if (mPlayerData.isAfrEnabled()) {
            applyAfrStopDelayed();
        }
    }

    private void applyAfrStopDelayed() {
        Utils.postDelayed(mApplyAfrStop, 200);
    }

    private void applyAfrStop() {
        // Send data to AFR daemon via tvQuickActions app
        TvQuickActions.sendStopAFR(getContext());
    }

    private void onFpsCorrectionClick() {
        mAutoFrameRateHelper.setFpsCorrectionEnabled(mPlayerData.isAfrFpsCorrectionEnabled());
    }

    private void onResolutionSwitchClick() {
        mAutoFrameRateHelper.setResolutionSwitchEnabled(mPlayerData.isAfrResSwitchEnabled(), mPlayerData.isAfrEnabled());
    }

    private void onDoubleRefreshRateClick() {
        mAutoFrameRateHelper.setDoubleRefreshRateEnabled(mPlayerData.isDoubleRefreshRateEnabled());
    }

    public void onSkip24RateClick() {
        mAutoFrameRateHelper.setSkip24RateEnabled(mPlayerData.isSkip24RateEnabled());
    }

    private void applyAfrWrapper() {
        if (mPlayerData.isAfrEnabled()) {
            AppDialogPresenter.instance(getContext()).showDialogMessage("Applying AFR...", this::applyAfr, 1_000);
        }
    }

    private void applyAfrDelayed() {
        Utils.postDelayed(mApplyAfr, 500);
    }

    private void applyAfr() {
        if (!skipAfr() && mPlayerData.isAfrEnabled()) {
            FormatItem videoFormat = getPlayer().getVideoFormat();
            applyAfr(videoFormat, false);
            // Send data to AFR daemon via tvQuickActions app
            TvQuickActions.sendStartAFR(getContext(), videoFormat);
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

    private void applyAfr(FormatItem videoFormat, boolean force) {
        if (videoFormat != null) {
            String msg = String.format("Applying afr... fps: %s, resolution: %sx%s, activity: %s",
                    videoFormat.getFrameRate(),
                    videoFormat.getWidth(),
                    videoFormat.getHeight(),
                    getContext().getClass().getSimpleName()
            );
            Log.d(TAG, msg);

            mAutoFrameRateHelper.apply(getActivity(), videoFormat, force);
        }
    }

    private void maybePausePlayback() {
        if (getPlayer() == null) {
            return;
        }

        int delayMs = 5_000;

        if (mPlayerData.getAfrPauseMs() > 0) {
            getPlayer().setPlayWhenReady(false);
            delayMs = mPlayerData.getAfrPauseMs();
        }

        Utils.postDelayed(mPlaybackResumeHandler, delayMs);
    }

    private void savePlayback() {
        if (!skipAfr() && mAutoFrameRateHelper.isSupported() && mPlayerData.isAfrEnabled() && mPlayerData.getAfrPauseMs() > 0) {
            mStateUpdater.blockPlay(true);
        }

        mIsPlay = mStateUpdater.getPlayEnabled();
    }

    private void restorePlayback() {
        if (!skipAfr() && mAutoFrameRateHelper.isSupported() && mPlayerData.isAfrEnabled() && mPlayerData.getAfrPauseMs() > 0) {
            mStateUpdater.blockPlay(false);
            getPlayer().setPlayWhenReady(mIsPlay);
        }
    }

    // Avoid nested dialogs. They have problems with timings. So player controls may hide without user interaction.
    private void addUiOptions() {
        if (mAutoFrameRateHelper.isSupported()) {
            OptionCategory afrCategory = createAutoFrameRateCategory(
                    getContext(), PlayerData.instance(getContext()),
                    () -> {}, this::onResolutionSwitchClick, this::onFpsCorrectionClick, this::onDoubleRefreshRateClick, this::onSkip24RateClick);

            OptionCategory afrPauseCategory = createAutoFrameRatePauseCategory(
                    getContext(), PlayerData.instance(getContext()));

            OptionCategory modesCategory = createAutoFrameRateModesCategory(getContext());

            // Create nested dialogs

            List<OptionItem> options = new ArrayList<>();
            options.add(UiOptionItem.from(afrCategory.title, optionItem -> {
                AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(getContext());
                dialogPresenter.appendCategory(afrCategory);
                dialogPresenter.showDialog(afrCategory.title);
            }));
            options.add(UiOptionItem.from(afrPauseCategory.title, optionItem -> {
                AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(getContext());
                dialogPresenter.appendCategory(afrPauseCategory);
                dialogPresenter.showDialog(afrPauseCategory.title);
            }));
            options.add(UiOptionItem.from(modesCategory.title, optionItem -> {
                AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(getContext());
                dialogPresenter.appendCategory(modesCategory);
                dialogPresenter.showDialog(modesCategory.title);
            }));

            mHQController.addCategory(OptionCategory.from(AUTO_FRAME_RATE_ID, OptionCategory.TYPE_STRING_LIST, getContext().getString(R.string.auto_frame_rate), options));
            mHQController.addOnDialogHide(mApplyAfr); // Apply NEW Settings on dialog close
        } else {
            mHQController.removeCategory(AUTO_FRAME_RATE_ID);
            mHQController.removeOnDialogHide(mApplyAfr);
        }
    }

    public static OptionCategory createAutoFrameRateCategory(Context context, PlayerData playerData) {
        return createAutoFrameRateCategory(context, playerData, () -> {}, () -> {}, () -> {}, () -> {}, () -> {});
    }

    private static OptionCategory createAutoFrameRateCategory(Context context, PlayerData playerData,
            Runnable onAfrCallback, Runnable onResolutionCallback, Runnable onFpsCorrectionCallback,
            Runnable onDoubleRefreshRateCallback, Runnable onSkip24RateCallback) {
        String afrEnable = context.getString(R.string.auto_frame_rate);
        String afrEnableDesc = context.getString(R.string.auto_frame_rate_desc);
        String fpsCorrection = context.getString(R.string.frame_rate_correction, "24->23.97, 30->29.97, 60->59.94");
        String resolutionSwitch = context.getString(R.string.resolution_switch);
        String doubleRefreshRate = context.getString(R.string.double_refresh_rate);
        String skip24Rate = context.getString(R.string.skip_24_rate);
        List<OptionItem> options = new ArrayList<>();

        OptionItem afrEnableOption = UiOptionItem.from(afrEnable, afrEnableDesc, optionItem -> {
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
        OptionItem doubleRefreshRateOption = UiOptionItem.from(doubleRefreshRate, optionItem -> {
            playerData.setDoubleRefreshRateEnabled(optionItem.isSelected());
            onDoubleRefreshRateCallback.run();
        }, playerData.isDoubleRefreshRateEnabled());
        OptionItem skip24RateOption = UiOptionItem.from(skip24Rate, optionItem -> {
            playerData.enableSkip24Rate(optionItem.isSelected());
            onSkip24RateCallback.run();
        }, playerData.isSkip24RateEnabled());

        afrResSwitchOption.setRequired(afrEnableOption);
        afrFpsCorrectionOption.setRequired(afrEnableOption);
        doubleRefreshRateOption.setRequired(afrEnableOption);
        skip24RateOption.setRequired(afrEnableOption);

        options.add(afrEnableOption);
        options.add(afrResSwitchOption);
        options.add(afrFpsCorrectionOption);
        options.add(doubleRefreshRateOption);
        options.add(skip24RateOption);

        return OptionCategory.from(AUTO_FRAME_RATE_ID, OptionCategory.TYPE_CHECKBOX_LIST, afrEnable, options);
    }

    public static OptionCategory createAutoFrameRatePauseCategory(Context context, PlayerData playerData) {
        String title = context.getString(R.string.auto_frame_rate_pause);

        List<OptionItem> options = new ArrayList<>();

        for (int pauseMs : Helpers.range(0, 15_000, 250)) {
            @SuppressLint("StringFormatMatches")
            String optionTitle = pauseMs == 0 ? context.getString(R.string.option_never) : context.getString(R.string.auto_frame_rate_sec, pauseMs / 1_000f);
            options.add(UiOptionItem.from(optionTitle,
                    optionItem -> {
                        playerData.setAfrPauseMs(pauseMs);
                        playerData.setAfrEnabled(true);
                    },
                    pauseMs == playerData.getAfrPauseMs()));
        }

        return OptionCategory.from(AUTO_FRAME_RATE_DELAY_ID, OptionCategory.TYPE_RADIO_LIST, title, options);
    }

    public static OptionCategory createAutoFrameRateModesCategory(Context context) {
        String title = context.getString(R.string.auto_frame_rate_modes);

        UhdHelper uhdHelper = new UhdHelper(context);

        Mode[] supportedModes = uhdHelper.getSupportedModes();
        Arrays.sort(supportedModes);

        StringBuilder result = new StringBuilder();

        for (Mode mode : supportedModes) {
            result.append(String.format("%sx%s@%s\n", mode.getPhysicalWidth(), mode.getPhysicalHeight(), mode.getRefreshRate()));
        }

        return OptionCategory.from(AUTO_FRAME_RATE_MODES_ID, OptionCategory.TYPE_LONG_TEXT, title, UiOptionItem.from(result.toString()));
    }

    private boolean skipAfr() {
        if (mPlayerData == null || getPlayer() == null || getPlayer().getVideo() == null) {
            return true;
        }

        return getPlayer().getDurationMs() <= SHORTS_DURATION_MS || getPlayer().getVideo().isShorts;
    }
}
