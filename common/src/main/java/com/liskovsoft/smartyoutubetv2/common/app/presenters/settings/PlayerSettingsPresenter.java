package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.AutoFrameRateManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.HqDialogManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.PlayerUiManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.misc.LangUpdater;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

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
        appendBackgroundPlaybackCategory(settingsPresenter);
        appendAutoFrameRateCategory(settingsPresenter);
        appendSubtitleLanguageCategory(settingsPresenter);
        appendSubtitleStyleCategory(settingsPresenter);
        appendOKButtonCategory(settingsPresenter);
        appendUIAutoHideCategory(settingsPresenter);
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

    private void appendAutoFrameRateCategory(AppSettingsPresenter settingsPresenter) {
        OptionCategory category = AutoFrameRateManager.createAutoFrameRateCategory(getContext(), mPlayerData);
        settingsPresenter.appendCheckedCategory(category.title, category.options);
    }

    private void appendSubtitleLanguageCategory(AppSettingsPresenter settingsPresenter) {
        String subtitleLanguageTitle = getContext().getString(R.string.subtitle_language);
        String subtitlesDisabled = getContext().getString(R.string.subtitles_disabled);

        LangUpdater langUpdater = new LangUpdater(getContext());
        HashMap<String, String> locales = langUpdater.getSupportedLocales();
        FormatItem currentFormat = mPlayerData.getFormat(FormatItem.TYPE_SUBTITLE);

        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(
                subtitlesDisabled, option -> mPlayerData.setFormat(FormatItem.fromLanguage(null)),
                currentFormat == null || currentFormat.equals(FormatItem.fromLanguage(null))));

        for (Entry<String, String> entry : locales.entrySet()) {
            options.add(UiOptionItem.from(
                    entry.getKey(), option -> mPlayerData.setFormat(FormatItem.fromLanguage(entry.getValue())),
                    FormatItem.fromLanguage(entry.getValue()).equals(currentFormat)));
        }
        
        settingsPresenter.appendRadioCategory(subtitleLanguageTitle, options);
    }

    private void appendSubtitleStyleCategory(AppSettingsPresenter settingsPresenter) {
        OptionCategory category = PlayerUiManager.createSubtitleStylesCategory(getContext(), mPlayerData);
        settingsPresenter.appendRadioCategory(category.title, category.options);
    }

    private void appendMiscCategory(AppSettingsPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.player_full_date),
                option -> mPlayerData.showFullDate(option.isSelected()),
                mPlayerData.isShowFullDateEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_seek_preview),
                option -> mPlayerData.enableSeekPreview(option.isSelected()),
                mPlayerData.isSeekPreviewEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_pause_when_seek),
                option -> mPlayerData.enablePauseOnSeek(option.isSelected()),
                mPlayerData.isPauseOnSeekEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_show_clock),
                option -> mPlayerData.enableClock(option.isSelected()),
                mPlayerData.isClockEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_show_remaining_time),
                option -> mPlayerData.enableRemainingTime(option.isSelected()),
                mPlayerData.isRemainingTimeEnabled()));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.player_other), options);
    }
}
