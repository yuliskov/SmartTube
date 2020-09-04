package com.liskovsoft.smartyoutubetv2.common.app.views;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Header;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

public interface MainView {
    void updateHeader(VideoGroup row);
    void clearHeader(Header header);
}
