package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

import java.util.ArrayList;
import java.util.List;

public class PlayerSettingsPresenter extends BasePresenter<Void> {
    private final PlayerData mPlayerUIData;

    public PlayerSettingsPresenter(Context context) {
        super(context);
        mPlayerUIData = PlayerData.instance(context);
    }

    public static PlayerSettingsPresenter instance(Context context) {
        return new PlayerSettingsPresenter(context);
    }

    public void show() {
        AppSettingsPresenter settingsPresenter = AppSettingsPresenter.instance(getContext());
        settingsPresenter.clear();

        appendOKButtonCategory(settingsPresenter);
        appendUIAutoHideCategory(settingsPresenter);
        appendMiscCategory(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.dialog_player_ui));
    }

    private void appendOKButtonCategory(AppSettingsPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(
                getContext().getString(R.string.player_only_ui),
                option -> mPlayerUIData.setOKButtonBehavior(PlayerData.ONLY_UI),
                mPlayerUIData.getOKButtonBehavior() == PlayerData.ONLY_UI));

        options.add(UiOptionItem.from(
                getContext().getString(R.string.player_ui_and_pause),
                option -> mPlayerUIData.setOKButtonBehavior(PlayerData.UI_AND_PAUSE),
                mPlayerUIData.getOKButtonBehavior() == PlayerData.UI_AND_PAUSE));

        options.add(UiOptionItem.from(
                getContext().getString(R.string.player_only_pause),
                option -> mPlayerUIData.setOKButtonBehavior(PlayerData.ONLY_PAUSE),
                mPlayerUIData.getOKButtonBehavior() == PlayerData.ONLY_PAUSE));

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.player_ok_button_behavior), options);
    }

    private void appendUIAutoHideCategory(AppSettingsPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(
                getContext().getString(R.string.player_ui_hide_never),
                option -> mPlayerUIData.setUIHideTimoutSec(PlayerData.AUTO_HIDE_NEVER),
                mPlayerUIData.getUIHideTimoutSec() == PlayerData.AUTO_HIDE_NEVER));

        for (int i = 1; i <= 15; i++) {
            int timeoutSec = i;
            options.add(UiOptionItem.from(
                    String.format("%s sec", i),
                    option -> mPlayerUIData.setUIHideTimoutSec(timeoutSec),
                    mPlayerUIData.getUIHideTimoutSec() == i));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.player_ui_hide_behavior), options);
    }

    private void appendMiscCategory(AppSettingsPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.player_full_date),
                option -> mPlayerUIData.showFullDate(option.isSelected()),
                mPlayerUIData.isShowFullDateEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_seek_preview),
                option -> mPlayerUIData.enableSeekPreview(option.isSelected()),
                mPlayerUIData.isSeekPreviewEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_pause_when_seek),
                option -> mPlayerUIData.enablePauseOnSeek(option.isSelected()),
                mPlayerUIData.isPauseOnSeekEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_show_clock),
                option -> mPlayerUIData.enableClock(option.isSelected()),
                mPlayerUIData.isClockEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_show_remaining_time),
                option -> mPlayerUIData.enableRemainingTime(option.isSelected()),
                mPlayerUIData.isRemainingTimeEnabled()));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.player_other), options);
    }
}
