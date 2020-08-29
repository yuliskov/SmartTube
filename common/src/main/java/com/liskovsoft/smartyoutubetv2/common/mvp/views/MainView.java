package com.liskovsoft.smartyoutubetv2.common.mvp.views;

import com.liskovsoft.smartyoutubetv2.common.mvp.models.Header;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.Video;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.VideoGroup;

public interface MainView {
    void updateHeader(VideoGroup row, Header header);
    void clearHeader(Header header);
    void showOnboarding();
    void openPlaybackView();
    void openDetailsView(Video item);
}
