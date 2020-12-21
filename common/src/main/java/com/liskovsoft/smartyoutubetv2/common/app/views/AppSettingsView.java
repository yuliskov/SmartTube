package com.liskovsoft.smartyoutubetv2.common.app.views;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter.SettingsCategory;

import java.util.List;

public interface AppSettingsView {
    void addCategories(List<SettingsCategory> categories);
    void setTitle(String title);
    void clear();
    void finish();
}
