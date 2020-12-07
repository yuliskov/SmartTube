package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData.ColorScheme;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class MainUISettingsPresenter extends BasePresenter<Void> {
    private final MainUIData mMainUIData;
    private boolean mRestartApp;

    public MainUISettingsPresenter(Context context) {
        super(context);
        mMainUIData = MainUIData.instance(context);
    }

    public static MainUISettingsPresenter instance(Context context) {
        return new MainUISettingsPresenter(context);
    }

    public void show() {
        AppSettingsPresenter settingsPresenter = AppSettingsPresenter.instance(getContext());
        settingsPresenter.clear();

        appendCardsStyle(settingsPresenter);
        appendColorScheme(settingsPresenter);
        appendVideoGridScale(settingsPresenter);
        appendScaleUI(settingsPresenter);
        appendLeftPanelCategories(settingsPresenter);
        appendBootToCategory(settingsPresenter);
        appendChannelSortingCategory(settingsPresenter);
        appendPlaylistsStyle(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.dialog_main_ui), () -> {
            if (mRestartApp) {
                mRestartApp = false;
                ViewManager.instance(getContext()).restartApp();
            }
        });
    }

    private void appendCardsStyle(AppSettingsPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        OptionItem animatedPreviewsOption = UiOptionItem.from(getContext().getString(R.string.animated_previews),
                option -> mMainUIData.enableAnimatedPreviews(option.isSelected()), mMainUIData.isAnimatedPreviewsEnabled());

        OptionItem dontCutTextOnCards = UiOptionItem.from(getContext().getString(R.string.multiline_titles),
                option -> mMainUIData.enableMultilineTitles(option.isSelected()), mMainUIData.isMultilineTitlesEnabled());

        options.add(animatedPreviewsOption);
        options.add(dontCutTextOnCards);

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.cards_style), options);
    }

    private void appendVideoGridScale(AppSettingsPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (float scale : new float[] {1.0f, 1.35f}) {
            options.add(UiOptionItem.from(String.format("%sx", scale),
                    optionItem -> mMainUIData.setVideoGridScale(scale),
                    Helpers.floatEquals(scale, mMainUIData.getVideoGridScale())));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.video_grid_scale), options);
    }

    private void appendScaleUI(AppSettingsPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (float scale : new float[] {0.6f, 0.65f, 0.7f, 0.75f, 0.8f, 0.85f, 0.9f, 0.95f, 1.0f}) {
            options.add(UiOptionItem.from(String.format("%sx", scale),
                    optionItem -> {
                        mMainUIData.setUIScale(scale);
                        mRestartApp = true;
                    },
                    Helpers.floatEquals(scale, mMainUIData.getUIScale())));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.scale_ui), options);
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

    private void appendChannelSortingCategory(AppSettingsPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.sorting_by_new_content, MainUIData.CHANNEL_SORTING_UPDATE},
                {R.string.sorting_alphabetically, MainUIData.CHANNEL_SORTING_AZ},
                {R.string.sorting_last_viewed, MainUIData.CHANNEL_SORTING_LAST_VIEWED}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]), optionItem -> {
                mMainUIData.setChannelCategorySorting(pair[1]);
                BrowsePresenter.instance(getContext()).updateChannelCategorySorting();
            }, mMainUIData.getChannelCategorySorting() == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.channel_category_sorting), options);
    }

    private void appendPlaylistsStyle(AppSettingsPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.playlists_style_grid, MainUIData.PLAYLISTS_STYLE_GRID},
                {R.string.playlists_style_rows, MainUIData.PLAYLISTS_STYLE_ROWS}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]), optionItem -> {
                mMainUIData.setPlaylistsStyle(pair[1]);
                BrowsePresenter.instance(getContext()).updatePlaylistsStyle();
            }, mMainUIData.getPlaylistsStyle() == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.playlists_style), options);
    }

    private void appendColorScheme(AppSettingsPresenter settingsPresenter) {
        List<ColorScheme> colorSchemes = mMainUIData.getColorSchemes();

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.color_scheme), fromColorSchemes(colorSchemes));
    }

    private List<OptionItem> fromColorSchemes(List<ColorScheme> colorSchemes) {
        List<OptionItem> styleOptions = new ArrayList<>();

        for (ColorScheme colorScheme : colorSchemes) {
            styleOptions.add(UiOptionItem.from(
                    getContext().getString(colorScheme.nameResId),
                    option -> {
                        mMainUIData.setColorScheme(colorScheme);
                        mRestartApp = true;
                    },
                    colorScheme.equals(mMainUIData.getColorScheme())));
        }

        return styleOptions;
    }
}
