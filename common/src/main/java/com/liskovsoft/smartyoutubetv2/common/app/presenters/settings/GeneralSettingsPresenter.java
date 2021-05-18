package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.HQDialogManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class GeneralSettingsPresenter extends BasePresenter<Void> {
    private final MainUIData mMainUIData;
    private final PlayerData mPlayerData;
    private boolean mRestartApp;

    public GeneralSettingsPresenter(Context context) {
        super(context);
        mMainUIData = MainUIData.instance(context);
        mPlayerData = PlayerData.instance(context);
    }

    public static GeneralSettingsPresenter instance(Context context) {
        return new GeneralSettingsPresenter(context);
    }

    public void show() {
        AppSettingsPresenter settingsPresenter = AppSettingsPresenter.instance(getContext());
        settingsPresenter.clear();

        appendLeftPanelCategories(settingsPresenter);
        appendBootToCategory(settingsPresenter);
        appendAppExitCategory(settingsPresenter);
        appendBackgroundPlaybackCategory(settingsPresenter);
        appendBackgroundPlaybackActivationCategory(settingsPresenter);
        appendMiscCategory(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.settings_general), () -> {
            if (mRestartApp) {
                mRestartApp = false;
                MessageHelpers.showLongMessage(getContext(), R.string.msg_restart_app);
            }
        });
    }

    private void appendLeftPanelCategories(AppSettingsPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        Map<Integer, Integer> leftPanelCategories = mMainUIData.getCategories();

        for (Entry<Integer, Integer> category : leftPanelCategories.entrySet()) {
             options.add(UiOptionItem.from(getContext().getString(category.getKey()), optionItem -> {
                 mMainUIData.enableCategory(category.getValue(), optionItem.isSelected());
                 BrowsePresenter.instance(getContext()).updateCategories();
             }, mMainUIData.isCategoryEnabled(category.getValue())));
        }

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.side_panel_sections), options);
    }

    private void appendBootToCategory(AppSettingsPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        Map<Integer, Integer> leftPanelCategories = mMainUIData.getCategories();

        for (Entry<Integer, Integer> category : leftPanelCategories.entrySet()) {
            options.add(UiOptionItem.from(getContext().getString(category.getKey()),
            optionItem -> mMainUIData.setBootCategoryId(category.getValue()),
            category.getValue().equals(mMainUIData.getBootCategoryId())));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.boot_to_section), options);
    }

    private void appendAppExitCategory(AppSettingsPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.app_exit_none, MainUIData.EXIT_NONE},
                {R.string.app_double_back_exit, MainUIData.EXIT_DOUBLE_BACK},
                {R.string.app_single_back_exit, MainUIData.EXIT_SINGLE_BACK}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]),
                    optionItem -> mMainUIData.setAppExitShortcut(pair[1]),
                    mMainUIData.getAppExitShortcut() == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.app_exit_shortcut), options);
    }

    private void appendBackgroundPlaybackCategory(AppSettingsPresenter settingsPresenter) {
        OptionCategory category = HQDialogManager.createBackgroundPlaybackCategory(getContext(), mPlayerData);
        settingsPresenter.appendRadioCategory(category.title, category.options);
    }

    private void appendBackgroundPlaybackActivationCategory(AppSettingsPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from("HOME",
                option -> mMainUIData.setBackgroundShortcut(MainUIData.BACKGROUND_SHORTCUT_HOME),
                mMainUIData.getBackgroundShortcut() == MainUIData.BACKGROUND_SHORTCUT_HOME));

        options.add(UiOptionItem.from("HOME/BACK",
                option -> mMainUIData.setBackgroundShortcut(MainUIData.BACKGROUND_SHORTCUT_HOME_N_BACK),
                mMainUIData.getBackgroundShortcut() == MainUIData.BACKGROUND_SHORTCUT_HOME_N_BACK));

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.background_playback_activation), options);
    }

    private void appendMiscCategory(AppSettingsPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.return_to_launcher),
                option -> mMainUIData.enableReturnToLauncher(option.isSelected()),
                mMainUIData.isReturnToLauncherEnabled()));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.player_other), options);
    }
}
