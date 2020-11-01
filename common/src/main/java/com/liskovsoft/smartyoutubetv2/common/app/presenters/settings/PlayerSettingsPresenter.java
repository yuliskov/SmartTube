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
                mContext.getString(R.string.player_only_ui),
                option -> mPlayerUIData.setOKButtonBehavior(PlayerData.ONLY_UI),
                mPlayerUIData.getOKButtonBehavior() == PlayerData.ONLY_UI));

        options.add(UiOptionItem.from(
                mContext.getString(R.string.player_ui_and_pause),
                option -> mPlayerUIData.setOKButtonBehavior(PlayerData.UI_AND_PAUSE),
                mPlayerUIData.getOKButtonBehavior() == PlayerData.UI_AND_PAUSE));

        options.add(UiOptionItem.from(
                mContext.getString(R.string.player_only_pause),
                option -> mPlayerUIData.setOKButtonBehavior(PlayerData.ONLY_PAUSE),
                mPlayerUIData.getOKButtonBehavior() == PlayerData.ONLY_PAUSE));

        AppSettingsPresenter settingsPresenter = AppSettingsPresenter.instance(mContext);
        settingsPresenter.clear();
        settingsPresenter.appendRadioCategory(mContext.getString(R.string.player_ok_button_behavior), options);
        settingsPresenter.showDialog(mContext.getString(R.string.dialog_player_ui));
    }
}
