package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.AppDataSourceManager;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides a search feature for the Settings page.
 * Dynamically harvests all categories and option items from each settings presenter
 * to build a searchable index. Search results show breadcrumbs:
 * "Option → Section › Category" so the user knows exactly where to find the matched setting.
 */
public class SettingsSearchPresenter extends BasePresenter<Void> {

    private SettingsSearchPresenter(Context context) {
        super(context);
    }

    public static SettingsSearchPresenter instance(Context context) {
        return new SettingsSearchPresenter(context);
    }

    public void show() {
        SimpleEditDialog.show(
                getContext(),
                getContext().getString(R.string.search_in_settings),
                getContext().getString(R.string.search_in_settings_hint),
                null,
                newValue -> {
                    performSearch(newValue);
                    return true;
                }
        );
    }

    private void performSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }

        String lowerQuery = query.toLowerCase().trim();
        List<OptionItem> results = new ArrayList<>();
        Context context = getContext();

        if (context == null) {
            return;
        }

        // Build the dynamic search index from all settings presenters
        Map<SettingsItem, List<OptionCategory>> searchIndex = buildDynamicSearchIndex(context);

        for (Map.Entry<SettingsItem, List<OptionCategory>> entry : searchIndex.entrySet()) {
            SettingsItem settingsItem = entry.getKey();
            List<OptionCategory> categories = entry.getValue();
            String settingsTitle = settingsItem.title;

            // Check if the top-level settings title matches
            if (settingsTitle != null && settingsTitle.toLowerCase().contains(lowerQuery)) {
                final Runnable onClick = settingsItem.onClick;
                results.add(UiOptionItem.from(
                        settingsTitle,
                        optionItem -> onClick.run()
                ));
            }

            // Check each category and its option items
            for (OptionCategory category : categories) {
                String categoryTitle = category.title;

                // Check if the category title matches
                if (categoryTitle != null && categoryTitle.toLowerCase().contains(lowerQuery)) {
                    final Runnable onClick = settingsItem.onClick;
                    String resultLabel = categoryTitle + "  \u2192  " + settingsTitle;
                    results.add(UiOptionItem.from(
                            resultLabel,
                            optionItem -> onClick.run()
                    ));
                }

                // Check individual option items within this category
                if (category.options != null) {
                    for (OptionItem option : category.options) {
                        CharSequence optionTitle = option.getTitle();
                        if (optionTitle != null && optionTitle.toString().toLowerCase().contains(lowerQuery)) {
                            final Runnable onClick = settingsItem.onClick;
                            // Show breadcrumb: "Option → Section › Category"
                            String breadcrumb;
                            if (categoryTitle != null) {
                                breadcrumb = optionTitle + "  \u2192  " + settingsTitle + " \u203A " + categoryTitle;
                            } else {
                                breadcrumb = optionTitle + "  \u2192  " + settingsTitle;
                            }
                            results.add(UiOptionItem.from(
                                    breadcrumb,
                                    optionItem -> onClick.run()
                            ));
                        }
                    }
                }
            }
        }

        showResults(results, query);
    }

    /**
     * Dynamically builds the search index by calling each settings presenter's
     * appendCategories() method on a temporary AppDialogPresenter, then harvesting
     * the categories. This ensures the index is always in sync with the actual settings.
     */
    private Map<SettingsItem, List<OptionCategory>> buildDynamicSearchIndex(Context context) {
        Map<SettingsItem, List<OptionCategory>> index = new LinkedHashMap<>();
        List<SettingsItem> settingItems = AppDataSourceManager.instance().getSettingItems(context);

        // Map section title -> presenter class that can populate categories
        // We use a map of title -> Runnable that populates a given AppDialogPresenter
        Map<String, PresenterCategoryProvider> providers = new LinkedHashMap<>();

        providers.put(context.getString(R.string.settings_general), dialogPresenter ->
                GeneralSettingsPresenter.instance(context).appendCategories(dialogPresenter));
        providers.put(context.getString(R.string.settings_player), dialogPresenter ->
                PlayerSettingsPresenter.instance(context).appendCategories(dialogPresenter));
        providers.put(context.getString(R.string.dialog_main_ui), dialogPresenter ->
                MainUISettingsPresenter.instance(context).appendCategories(dialogPresenter));
        providers.put(context.getString(R.string.settings_language_country), dialogPresenter ->
                LanguageSettingsPresenter.instance(context).appendCategories(dialogPresenter));
        providers.put(context.getString(R.string.subtitle_category_title), dialogPresenter ->
                SubtitleSettingsPresenter.instance(context).appendCategories(dialogPresenter));
        providers.put(context.getString(R.string.settings_search), dialogPresenter ->
                SearchSettingsPresenter.instance(context).appendCategories(dialogPresenter));
        providers.put(context.getString(R.string.content_block_provider), dialogPresenter ->
                SponsorBlockSettingsPresenter.instance(context).appendCategories(dialogPresenter));
        providers.put(context.getString(R.string.dearrow_provider), dialogPresenter ->
                DeArrowSettingsPresenter.instance(context).appendCategories(dialogPresenter));
        providers.put(context.getString(R.string.auto_frame_rate), dialogPresenter ->
                AutoFrameRateSettingsPresenter.instance(context).appendCategories(dialogPresenter));
        providers.put(context.getString(R.string.app_backup_restore), dialogPresenter ->
                BackupSettingsPresenter.instance(context).appendCategories(dialogPresenter));
        providers.put(context.getString(R.string.settings_remote_control), dialogPresenter ->
                RemoteControlSettingsPresenter.instance(context).appendCategories(dialogPresenter));

        for (SettingsItem item : settingItems) {
            if (item.title == null) continue;
            // Skip the search card itself
            if (item.title.equals(context.getString(R.string.search_in_settings))) continue;

            PresenterCategoryProvider provider = providers.get(item.title);
            if (provider != null) {
                // Create a temporary non-singleton AppDialogPresenter to harvest categories
                AppDialogPresenter tempPresenter = new AppDialogPresenter(context);
                try {
                    provider.appendCategories(tempPresenter);
                } catch (Exception e) {
                    // If there's any error populating categories, skip this section
                }
                index.put(item, new ArrayList<>(tempPresenter.getCategories()));
            } else {
                index.put(item, new ArrayList<>());
            }
        }

        return index;
    }

    /**
     * Functional interface for providing categories to a dialog presenter.
     */
    private interface PresenterCategoryProvider {
        void appendCategories(AppDialogPresenter dialogPresenter);
    }

    private void showResults(List<OptionItem> results, String query) {
        Context context = getContext();
        if (context == null) {
            return;
        }

        AppDialogPresenter presenter = AppDialogPresenter.instance(context);

        if (results.isEmpty()) {
            List<OptionItem> emptyList = new ArrayList<>();
            emptyList.add(UiOptionItem.from(
                    context.getString(R.string.nothing_found) + ": \"" + query + "\""
            ));
            presenter.appendStringsCategory(
                    context.getString(R.string.search_in_settings),
                    emptyList
            );
        } else {
            presenter.appendStringsCategory(
                    context.getString(R.string.search_in_settings) + " (" + results.size() + ")",
                    results
            );
        }

        presenter.showDialog(context.getString(R.string.search_in_settings));
    }
}
