package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.HQDialogManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.PlayerUIManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;

import java.util.ArrayList;
import java.util.List;

public class PlayerSettingsPresenter extends BasePresenter<Void> {
    private final PlayerData mPlayerData;
    private final PlayerTweaksData mPlayerTweaksData;

    public PlayerSettingsPresenter(Context context) {
        super(context);
        mPlayerData = PlayerData.instance(context);
        mPlayerTweaksData = PlayerTweaksData.instance(context);
    }

    public static PlayerSettingsPresenter instance(Context context) {
        return new PlayerSettingsPresenter(context);
    }

    public void show() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());
        settingsPresenter.clear();

        appendVideoPresetsCategory(settingsPresenter);
        appendVideoBufferCategory(settingsPresenter);
        //appendVideoZoomCategory(settingsPresenter);
        appendAudioShiftCategory(settingsPresenter);
        //appendBackgroundPlaybackCategory(settingsPresenter);
        appendOKButtonCategory(settingsPresenter);
        appendUIAutoHideCategory(settingsPresenter);
        appendSeekingPreviewCategory(settingsPresenter);
        appendRememberSpeedCategory(settingsPresenter);
        appendMiscCategory(settingsPresenter);
        appendTweaksCategory(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.dialog_player_ui));
    }

    private void appendOKButtonCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(
                getContext().getString(R.string.player_only_ui),
                option -> mPlayerData.setOKButtonBehavior(PlayerData.ONLY_UI),
                mPlayerData.getOKButtonBehavior() == PlayerData.ONLY_UI));

        options.add(UiOptionItem.from(
                getContext().getString(R.string.player_ui_and_pause),
                option -> mPlayerData.setOKButtonBehavior(PlayerData.UI_AND_PAUSE),
                mPlayerData.getOKButtonBehavior() == PlayerData.UI_AND_PAUSE));

        options.add(UiOptionItem.from(
                getContext().getString(R.string.player_only_pause),
                option -> mPlayerData.setOKButtonBehavior(PlayerData.ONLY_PAUSE),
                mPlayerData.getOKButtonBehavior() == PlayerData.ONLY_PAUSE));

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.player_ok_button_behavior), options);
    }

    private void appendUIAutoHideCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(
                getContext().getString(R.string.option_never),
                option -> mPlayerData.setUIHideTimoutSec(PlayerData.AUTO_HIDE_NEVER),
                mPlayerData.getUIHideTimoutSec() == PlayerData.AUTO_HIDE_NEVER));

        for (int i = 1; i <= 15; i++) {
            int timeoutSec = i;
            options.add(UiOptionItem.from(
                    String.format("%s sec", i),
                    option -> mPlayerData.setUIHideTimoutSec(timeoutSec),
                    mPlayerData.getUIHideTimoutSec() == i));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.player_ui_hide_behavior), options);
    }

    private void appendVideoBufferCategory(AppDialogPresenter settingsPresenter) {
        OptionCategory category = HQDialogManager.createVideoBufferCategory(getContext(), mPlayerData);
        settingsPresenter.appendRadioCategory(category.title, category.options);
    }

    private void appendVideoPresetsCategory(AppDialogPresenter settingsPresenter) {
        OptionCategory category = HQDialogManager.createVideoPresetsCategory(getContext(), mPlayerData);
        settingsPresenter.appendRadioCategory(category.title, category.options);
    }

    private void appendVideoZoomCategory(AppDialogPresenter settingsPresenter) {
        OptionCategory category = PlayerUIManager.createVideoZoomCategory(getContext(), mPlayerData);
        settingsPresenter.appendRadioCategory(category.title, category.options);
    }

    private void appendAudioShiftCategory(AppDialogPresenter settingsPresenter) {
        OptionCategory category = HQDialogManager.createAudioShiftCategory(getContext(), mPlayerData);
        settingsPresenter.appendRadioCategory(category.title, category.options);
    }

    private void appendSeekingPreviewCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.player_seek_preview_none, PlayerData.SEEK_PREVIEW_NONE},
                {R.string.player_seek_preview_single, PlayerData.SEEK_PREVIEW_SINGLE},
                {R.string.player_seek_preview_carousel_slow, PlayerData.SEEK_PREVIEW_CAROUSEL_SLOW},
                {R.string.player_seek_preview_carousel_fast, PlayerData.SEEK_PREVIEW_CAROUSEL_FAST}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]),
                    optionItem -> mPlayerData.setSeekPreviewMode(pair[1]),
                    mPlayerData.getSeekPreviewMode() == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.player_seek_preview), options);
    }

    private void appendRememberSpeedCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.player_remember_speed_none),
                optionItem -> {
                    mPlayerData.enableRememberSpeed(false);
                    mPlayerData.enableRememberSpeedEach(false);
                },
                !mPlayerData.isRememberSpeedEnabled() && !mPlayerData.isRememberSpeedEachEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_remember_speed_all),
                optionItem -> mPlayerData.enableRememberSpeed(true),
                mPlayerData.isRememberSpeedEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_remember_speed_each),
                optionItem -> mPlayerData.enableRememberSpeedEach(true),
                mPlayerData.isRememberSpeedEachEnabled()));

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.player_remember_speed), options);
    }

    private void appendTweaksCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from("Audio sync fix",
                option -> mPlayerTweaksData.enableAudioSyncFix(option.isSelected()),
                mPlayerTweaksData.isAudioSyncFixEnabled()));

        options.add(UiOptionItem.from("Amlogic 1080/60 fix",
                option -> mPlayerTweaksData.enableAmlogicFix(option.isSelected()),
                mPlayerTweaksData.isAmlogicFixEnabled()));

        options.add(UiOptionItem.from("Ambilight fix",
                option -> mPlayerTweaksData.enableTextureView(option.isSelected()),
                mPlayerTweaksData.isTextureViewEnabled()));

        options.add(UiOptionItem.from("Sleep timer fix",
                option -> mPlayerData.enableSonyTimerFix(option.isSelected()),
                mPlayerData.isSonyTimerFixEnabled()));

        options.add(UiOptionItem.from("Disable snap to vsync",
                option -> mPlayerTweaksData.disableSnapToVsync(option.isSelected()),
                mPlayerTweaksData.isSnappingToVsyncDisabled()));

        options.add(UiOptionItem.from("Skip codec profile level check",
                option -> mPlayerTweaksData.skipProfileLevelCheck(option.isSelected()),
                mPlayerTweaksData.isProfileLevelCheckSkipped()));

        options.add(UiOptionItem.from("Force legacy codecs",
                option -> mPlayerData.enableLowQuality(option.isSelected()),
                mPlayerData.isLowQualityEnabled()));

        options.add(UiOptionItem.from("Force SW video decoder",
                option -> mPlayerTweaksData.forceSWDecoder(option.isSelected()),
                mPlayerTweaksData.isSWDecoderForced()));

        options.add(UiOptionItem.from("Frame drops fix (experimental)",
                option -> mPlayerTweaksData.enableFrameDropFix(option.isSelected()),
                mPlayerTweaksData.isFrameDropFixEnabled()));

        // Need to be enabled on older version of ExoPlayer (e.g. 2.10.6).
        // It's because there's no tweaks for modern devices.
        //options.add(UiOptionItem.from("Enable set output surface workaround",
        //        option -> mPlayerTweaksData.enableSetOutputSurfaceWorkaround(option.isSelected()),
        //        mPlayerTweaksData.isSetOutputSurfaceWorkaroundEnabled()));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.player_tweaks), options);
    }

    private void appendMiscCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        //options.add(UiOptionItem.from(getContext().getString(R.string.player_full_date),
        //        option -> mPlayerData.enableAbsoluteDate(option.isSelected()),
        //        mPlayerData.isAbsoluteDateEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_pause_when_seek),
                option -> mPlayerData.enablePauseOnSeek(option.isSelected()),
                mPlayerData.isPauseOnSeekEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_show_clock),
                option -> mPlayerData.enableClock(option.isSelected()),
                mPlayerData.isClockEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_show_global_clock),
                option -> mPlayerData.enableGlobalClock(option.isSelected()),
                mPlayerData.isGlobalClockEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_show_remaining_time),
                option -> mPlayerData.enableRemainingTime(option.isSelected()),
                mPlayerData.isRemainingTimeEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_show_quality_info),
                option -> mPlayerData.enableQualityInfo(option.isSelected()),
                mPlayerData.isQualityInfoEnabled()));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.player_other), options);
    }
}
