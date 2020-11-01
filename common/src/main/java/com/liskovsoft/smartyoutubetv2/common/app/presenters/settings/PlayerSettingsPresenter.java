package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

import java.util.ArrayList;
import java.util.List;

public class PlayerSettingsPresenter {
    private final Context mContext;
    private final PlayerData mPlayerUIData;

    public PlayerSettingsPresenter(Context context) {
        mContext = context;
        mPlayerUIData = PlayerData.instance(context);
    }

    public static PlayerSettingsPresenter instance(Context context) {
        return new PlayerSettingsPresenter(context.getApplicationContext());
    }

    public void show() {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(
                mContext.getString(R.string.player_show_ui_on_pause),
                option -> mPlayerUIData.showUIOnPause(option.isSelected()),
                mPlayerUIData.isShowUIOnPauseEnabled()));

        options.add(UiOptionItem.from(
                mContext.getString(R.string.player_pause_on_ok),
                option -> mPlayerUIData.pauseOnOK(option.isSelected()),
                mPlayerUIData.isPauseOnOKEnabled()));

        AppSettingsPresenter settingsPresenter = AppSettingsPresenter.instance(mContext);
        settingsPresenter.clear();
        settingsPresenter.appendCheckedCategory(mContext.getString(R.string.dialog_player_ui), options);
        settingsPresenter.showDialog();
    }
}
