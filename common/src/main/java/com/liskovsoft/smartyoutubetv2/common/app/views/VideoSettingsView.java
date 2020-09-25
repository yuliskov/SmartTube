package com.liskovsoft.smartyoutubetv2.common.app.views;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.VideoSettingsPresenter.SettingsCategory;

import java.util.List;

public interface VideoSettingsView {
    void addCategories(List<SettingsCategory> categories);
    void setTitle(String title);
}
