package com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

public interface VideoGroupPresenter {
    void onVideoItemClicked(Video item);
    void onVideoItemLongClicked(Video item);
    void onScrollEnd(VideoGroup group);
}
