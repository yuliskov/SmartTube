package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.HqDialogManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.PlayerUiManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

import java.util.ArrayList;
import java.util.List;

public class PlayerSettingsPresenter extends BasePresenter<Void> {
    private final PlayerData mPlayerData;

    public PlayerSettingsPresenter(Context context) {
        super(context);
        mPlayerData = PlayerData.instance(context);
    }

    public static PlayerSettingsPresenter instance(Context context) {
        return new PlayerSettingsPresenter(context);
    }

    public void show() {
        AppSettingsPresenter settingsPresenter = AppSettingsPresenter.instance(getContext());
        settingsPresenter.clear();

        appendVideoBufferCategory(settingsPresenter);
        appendVideoPresetsCategory(settingsPresenter);
        appendVideoZoomCategory(settingsPresenter);
        appendAudioShiftCategory(settingsPresenter);
        appendBackgroundPlaybackCategory(settingsPresenter);
        appendOKButtonCategory(settingsPresenter);
        appendUIAutoHideCategory(settingsPresenter);
        appendSeekingPreviewCategory(settingsPresenter);
        appendMiscCategory(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.dialog_player_ui));
    }

    private void appendOKButtonCategory(AppSettingsPresenter settingsPresenter) {
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

    private void appendUIAutoHideCategory(AppSettingsPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(
                getContext().getString(R.string.player_ui_hide_never),
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

    private void appendVideoBufferCategory(AppSettingsPresenter settingsPresenter) {
        OptionCategory category = HqDialogManager.createVideoBufferCategory(getContext(), mPlayerData);
        settingsPresenter.appendRadioCategory(category.title, category.options);
    }

    private void appendVideoPresetsCategory(AppSettingsPresenter settingsPresenter) {
        OptionCategory category = HqDialogManager.createVideoPresetsCategory(getContext(), mPlayerData);
        settingsPresenter.appendRadioCategory(category.title, category.options);
    }

    private void appendBackgroundPlaybackCategory(AppSettingsPresenter settingsPresenter) {
        OptionCategory category = HqDialogManager.createBackgroundPlaybackCategory(getContext(), mPlayerData);
        settingsPresenter.appendRadioCategory(category.title, category.options);
    }

    private void appendVideoZoomCategory(AppSettingsPresenter settingsPresenter) {
        OptionCategory category = PlayerUiManager.createVideoZoomCategory(getContext(), mPlayerData);
        settingsPresenter.appendRadioCategory(category.title, category.options);
    }

    private void appendAudioShiftCategory(AppSettingsPresenter settingsPresenter) {
        OptionCategory category = HqDialogManager.createAudioShiftCategory(getContext(), mPlayerData);
        settingsPresenter.appendRadioCategory(category.title, category.options);
    }

    private void appendSeekingPreviewCategory(AppSettingsPresenter settingsPresenter) {
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

    private void appendMiscCategory(AppSettingsPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        //options.add(UiOptionItem.from(getContext().getString(R.string.player_full_date),
        //        option -> mPlayerData.showFullDate(option.isSelected()),
        //        mPlayerData.isShowFullDateEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_pause_when_seek),
                option -> mPlayerData.enablePauseOnSeek(option.isSelected()),
                mPlayerData.isPauseOnSeekEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_sleep_timer),
                option -> mPlayerData.enableSleepTimer(option.isSelected()),
                mPlayerData.isSleepTimerEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_show_clock),
                option -> mPlayerData.enableClock(option.isSelected()),
                mPlayerData.isClockEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_show_remaining_time),
                option -> mPlayerData.enableRemainingTime(option.isSelected()),
                mPlayerData.isRemainingTimeEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_remember_speed),
                option -> mPlayerData.enableRememberSpeed(option.isSelected()),
                mPlayerData.isRememberSpeedEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_low_video_quality),
                option -> mPlayerData.enableLowQuality(option.isSelected()),
                mPlayerData.isLowQualityEnabled()));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.player_other), options);
    }
}
