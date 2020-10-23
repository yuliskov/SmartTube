package com.liskovsoft.smartyoutubetv2.common.app.views;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Category;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

public interface BrowseView {
    void updateCategory(VideoGroup group);
    void updateCategory(SettingsGroup group);
    void addCategory(Category category);
    void clearCategory(Category category);
    void updateErrorIfEmpty(ErrorFragmentData data);
    void showProgressBar(boolean show);
}
