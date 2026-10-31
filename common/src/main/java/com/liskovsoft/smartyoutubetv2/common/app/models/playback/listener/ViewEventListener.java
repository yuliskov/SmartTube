package com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener;

public interface ViewEventListener {
    void onViewCreated();
    void onViewDestroyed();
    void onViewPaused();
    void onViewResumed();
}
