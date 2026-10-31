package com.liskovsoft.smartyoutubetv2.common.misc;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

public interface BrowseProcessor {
    interface OnItemReady {
        void onItemReady(Video video);
    }
    void process(VideoGroup videoGroup);
    void dispose();
}
