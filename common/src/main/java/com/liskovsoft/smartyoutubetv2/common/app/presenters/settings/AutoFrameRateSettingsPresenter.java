package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.AutoFrameRateManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

public class AutoFrameRateSettingsPresenter extends BasePresenter<Void> {
    private final PlayerData mPlayerData;

    public AutoFrameRateSettingsPresenter(Context context) {
        super(context);
        mPlayerData = PlayerData.instance(context);
    }

    public static AutoFrameRateSettingsPresenter instance(Context context) {
        return new AutoFrameRateSettingsPresenter(context);
    }

    public void show() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());
        settingsPresenter.clear();

        appendAutoFrameRateCategory(settingsPresenter);
        appendAutoFrameRatePauseCategory(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.auto_frame_rate));
    }

    private void appendAutoFrameRateCategory(AppDialogPresenter settingsPresenter) {
        OptionCategory category = AutoFrameRateManager.createAutoFrameRateCategory(getContext(), mPlayerData);
        settingsPresenter.appendCheckedCategory(category.title, category.options);
    }

    private void appendAutoFrameRatePauseCategory(AppDialogPresenter settingsPresenter) {
        OptionCategory category = AutoFrameRateManager.createAutoFrameRatePauseCategory(getContext(), mPlayerData);
        settingsPresenter.appendRadioCategory(category.title, category.options);
    }
}
