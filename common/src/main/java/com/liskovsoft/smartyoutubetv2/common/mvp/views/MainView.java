package com.liskovsoft.smartyoutubetv2.common.mvp.views;

import com.liskovsoft.smartyoutubetv2.common.mvp.models.Header;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.VideoGroup;

public interface MainView {
    void updateRow(VideoGroup group, Header header);
    void updateGrid(VideoGroup group, Header header);
    void clearRow(Header header);
    void clearGrid(Header header);
    void showOnboarding();
}
