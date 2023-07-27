package com.liskovsoft.smartyoutubetv2.common.app.views;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

public interface BrowseView {
    void addSection(int index, BrowseSection category);
    void removeSection(BrowseSection category);
    void selectSection(int index, boolean focusOnContent);
    void updateSection(VideoGroup group);
    void updateSection(SettingsGroup group);
    void clearSection(BrowseSection category);
    void selectSectionItem(int index);
    void showError(ErrorFragmentData data);
    void showProgressBar(boolean show);
    boolean isProgressBarShowing();
    void focusOnContent();
    boolean isEmpty();
}
