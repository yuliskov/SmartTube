package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import java.util.List;

public class SettingsGroup {
    private List<SettingsItem> mItems;
    private Category mCategory;

    public static SettingsGroup from(List<SettingsItem> items, Category category) {
        SettingsGroup settingsGroup = new SettingsGroup();
        settingsGroup.mItems = items;
        settingsGroup.mCategory = category;

        return settingsGroup;
    }

    public List<SettingsItem> getItems() {
        return mItems;
    }

    public Category getCategory() {
        return mCategory;
    }

    public boolean isEmpty() {
        return mItems == null || mItems.size() == 0;
    }

    public String getTitle() {
        return mCategory.getTitle();
    }
}
