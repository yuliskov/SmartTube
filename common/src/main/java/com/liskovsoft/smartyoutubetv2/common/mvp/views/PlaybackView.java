package com.liskovsoft.smartyoutubetv2.common.mvp.views;

import com.liskovsoft.smartyoutubetv2.common.mvp.models.VideoGroup;

public interface PlaybackView {
    void updateRelatedVideos(VideoGroup row);
}
