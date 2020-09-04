package com.liskovsoft.smartyoutubetv2.common.app.models;

public interface ViewEventListener {
    void onViewCreated();
    void onViewDestroyed();
    void onViewPaused();
    void onViewResumed();
}
