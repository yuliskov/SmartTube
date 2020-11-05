package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class MainUISettingsPresenter {
    private final Context mContext;
    private final MainUIData mMainUIData;

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
        appendLeftPanelCategories(settingsPresenter);
        appendBootToCategory(settingsPresenter);

        settingsPresenter.showDialog(mContext.getString(R.string.dialog_main_ui));
    }

    private void appendAnimatedPreviews(AppSettingsPresenter settingsPresenter) {
        OptionItem animatedPreviewsOption = UiOptionItem.from(mContext.getString(R.string.animated_previews),
                option -> mMainUIData.enableAnimatedPreviews(option.isSelected()), mMainUIData.isAnimatedPreviewsEnabled());

        settingsPresenter.appendSingleSwitch(animatedPreviewsOption);
    }

    private void appendLeftPanelCategories(AppSettingsPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        Map<Integer, Integer> leftPanelCategories = mMainUIData.getCategories();

        for (Entry<Integer, Integer> category : leftPanelCategories.entrySet()) {
             options.add(UiOptionItem.from(mContext.getString(category.getKey()), optionItem -> {
                 mMainUIData.setCategoryEnabled(category.getValue(), optionItem.isSelected());
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
            optionItem -> mMainUIData.setBootToCategoryId(category.getValue()),
            category.getValue().equals(mMainUIData.getBootToCategoryId())));
        }

        settingsPresenter.appendRadioCategory(mContext.getString(R.string.boot_to_section), options);
    }
}
