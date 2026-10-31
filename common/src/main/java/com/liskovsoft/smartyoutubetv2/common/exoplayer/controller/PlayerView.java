package com.liskovsoft.smartyoutubetv2.common.exoplayer.controller;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

public interface PlayerView {
    void setQualityInfo(String info);
    void setVideo(Video video);
}
