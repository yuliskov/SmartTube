package com.liskovsoft.smartyoutubetv2.common.app.presenters;

public interface HeaderPresenter<T> extends VideoGroupPresenter<T> {
    void onHeaderFocused(long headerId);
    void onViewResumed();
}
