package com.liskovsoft.smartyoutubetv2.common.app.views;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter.OptionCategory;

import java.util.List;

public interface AppDialogView {
    void setTitle(String title);
    void addCategories(List<OptionCategory> categories);
    void clear();
    void finish();
    void goBack();
    boolean isShown();
}
