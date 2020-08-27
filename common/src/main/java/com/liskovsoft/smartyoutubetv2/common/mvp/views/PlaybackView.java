package com.liskovsoft.smartyoutubetv2.common.mvp.views;

import com.liskovsoft.smartyoutubetv2.common.mvp.models.Video;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.VideoGroup;

import java.io.InputStream;

public interface PlaybackView {
    void updateRelatedVideos(VideoGroup row);
    Video getVideo();
    void loadDashStream(InputStream dashManifest);
}
