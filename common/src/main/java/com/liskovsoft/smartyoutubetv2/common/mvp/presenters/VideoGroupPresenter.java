package com.liskovsoft.smartyoutubetv2.common.mvp.presenters;

import com.liskovsoft.smartyoutubetv2.common.mvp.models.Video;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.VideoGroup;

public interface VideoGroupPresenter<T> extends Presenter<T> {
    void onVideoItemClicked(Video item);
    void onVideoItemLongClicked(Video item);
    void onScrollEnd(VideoGroup group);
}
