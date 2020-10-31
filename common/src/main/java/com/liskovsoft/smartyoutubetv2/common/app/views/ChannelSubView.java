package com.liskovsoft.smartyoutubetv2.common.app.views;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

public interface ChannelSubView {
    void update(VideoGroup videoGroup);
    void showProgressBar(boolean show);
    void clear();
}
