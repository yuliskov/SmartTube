package com.liskovsoft.smartyoutubetv2.views;

import com.liskovsoft.mediaserviceinterfaces.MediaGroup;

public interface AppView {
    void addHomeGroup(MediaGroup homeGroup);
    void continueHomeGroup(MediaGroup homeGroup);
    void showOnboarding();
}
