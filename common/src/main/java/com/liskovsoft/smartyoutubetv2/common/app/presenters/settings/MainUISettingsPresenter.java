package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class MainUISettingsPresenter {
    private final Context mContext;
    private final MainUIData mMainUIData;
    private boolean mRestartApp;

    public MainUISettingsPresenter(Context context) {
        mContext = context;
        mMainUIData = MainUIData.instance(context);
    }

    public static MainUISettingsPresenter instance(Context context) {
        return new MainUISettingsPresenter(context.getApplicationContext());
    }

    public void show() {
        AppSettingsPresenter settingsPresenter = AppSettingsPresenter.instance(mContext);
        settingsPresenter.clear();

        appendAnimatedPreviews(settingsPresenter);
        appendVideoGridSize(settingsPresenter);
        appendScaleUI(settingsPresenter);
        appendLeftPanelCategories(settingsPresenter);
        appendBootToCategory(settingsPresenter);

        settingsPresenter.showDialog(mContext.getString(R.string.dialog_main_ui), () -> {
            if (mRestartApp) {
                mRestartApp = false;
                ViewManager.instance(mContext).restartApp();
            }
        });
    }

    private void appendAnimatedPreviews(AppSettingsPresenter settingsPresenter) {
        OptionItem animatedPreviewsOption = UiOptionItem.from(mContext.getString(R.string.animated_previews),
                option -> mMainUIData.enableAnimatedPreviews(option.isSelected()), mMainUIData.isAnimatedPreviewsEnabled());

        settingsPresenter.appendSingleSwitch(animatedPreviewsOption);
    }

    private void appendVideoGridSize(AppSettingsPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from("4x3",
                optionItem -> mMainUIData.enableLargeGrid(false),
                !mMainUIData.isLargeGridEnabled()));

        options.add(UiOptionItem.from("3x2",
                optionItem -> mMainUIData.enableLargeGrid(true),
                mMainUIData.isLargeGridEnabled()));

        settingsPresenter.appendRadioCategory(mContext.getString(R.string.video_grid_size), options);
    }

    private void appendScaleUI(AppSettingsPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (float scale : new float[] {0.7f, 0.75f, 0.8f, 0.85f, 0.9f, 0.95f, 1.0f}) {
            options.add(UiOptionItem.from(String.format("%sx", scale),
                    optionItem -> {
                        mMainUIData.setUIScale(scale);
                        mRestartApp = true;
                    },
                    Helpers.floatEquals(scale, mMainUIData.getUIScale())));
        }

        settingsPresenter.appendRadioCategory(mContext.getString(R.string.scale_ui), options);
    }

    private void appendLargeUI(AppSettingsPresenter settingsPresenter) {
        OptionItem animatedPreviewsOption = UiOptionItem.from(mContext.getString(R.string.large_ui),
                option -> mMainUIData.enableLargeGrid(option.isSelected()), mMainUIData.isLargeGridEnabled());

        settingsPresenter.appendSingleSwitch(animatedPreviewsOption);
    }

    private void appendLeftPanelCategories(AppSettingsPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        Map<Integer, Integer> leftPanelCategories = mMainUIData.getCategories();

        for (Entry<Integer, Integer> category : leftPanelCategories.entrySet()) {
             options.add(UiOptionItem.from(mContext.getString(category.getKey()), optionItem -> {
                 mMainUIData.enableCategory(category.getValue(), optionItem.isSelected());
                 BrowsePresenter.instance(mContext).updateCategories();
             }, mMainUIData.isCategoryEnabled(category.getValue())));
        }

        settingsPresenter.appendCheckedCategory(mContext.getString(R.string.side_panel_sections), options);
    }

    private void appendBootToCategory(AppSettingsPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        Map<Integer, Integer> leftPanelCategories = mMainUIData.getCategories();

        for (Entry<Integer, Integer> category : leftPanelCategories.entrySet()) {
            options.add(UiOptionItem.from(mContext.getString(category.getKey()),
            optionItem -> mMainUIData.setBootCategoryId(category.getValue()),
            category.getValue().equals(mMainUIData.getBootCategoryId())));
        }

        settingsPresenter.appendRadioCategory(mContext.getString(R.string.boot_to_section), options);
    }
}
