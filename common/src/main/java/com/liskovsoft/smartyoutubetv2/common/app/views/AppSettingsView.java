package com.liskovsoft.smartyoutubetv2.common.app.views;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter.SettingsCategory;

import java.util.List;

public interface AppSettingsView {
    void setTitle(String title);
    void addCategories(List<SettingsCategory> categories);
    void clear();
    void finish();
}
