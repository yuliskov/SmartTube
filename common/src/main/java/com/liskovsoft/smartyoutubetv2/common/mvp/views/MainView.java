package com.liskovsoft.smartyoutubetv2.common.mvp.views;

import com.liskovsoft.smartyoutubetv2.common.mvp.models.data.Header;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.data.VideoGroup;

public interface MainView {
    void updateHeader(VideoGroup row);
    void clearHeader(Header header);
}
