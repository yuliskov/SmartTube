package com.liskovsoft.smartyoutubetv2.common.app.views;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter.OptionCategory;

import java.util.List;

public interface AppDialogView {
    void show(List<OptionCategory> categories, String title, boolean isExpandable);
    void finish();
    void goBack();
    boolean isShown();
}
