package com.liskovsoft.smartyoutubetv2.common.mvp.views;

import com.liskovsoft.smartyoutubetv2.common.mvp.models.Header;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.Video;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.VideoGroup;

public interface MainView {
    void updateRowHeader(VideoGroup row, Header header);
    void updateGridHeader(VideoGroup grid, Header header);
    void clearRowHeader(Header header);
    void clearGridHeader(Header header);
    void showOnboarding();
    void openPlaybackView();
    void openDetailsView(Video item);
}
