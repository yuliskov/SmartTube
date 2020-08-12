package com.liskovsoft.smartyoutubetv2.common.views;

import com.liskovsoft.mediaserviceinterfaces.MediaGroup;

public interface MainView {
    void addHomeGroup(MediaGroup homeGroup);
    void continueHomeGroup(MediaGroup homeGroup);
    void showOnboarding();
}
