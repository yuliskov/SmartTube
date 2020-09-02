package com.liskovsoft.smartyoutubetv2.common.mvp.presenters;

import com.liskovsoft.smartyoutubetv2.common.mvp.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.data.VideoGroup;

public interface VideoGroupPresenter<T> extends Presenter<T> {
    void onVideoItemClicked(Video item);
    void onVideoItemLongClicked(Video item);
    void onScrollEnd(VideoGroup group);
}
