package com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

public interface VideoGroupPresenter {
    void onVideoItemSelected(Video item);
    void onVideoItemClicked(Video item);
    void onVideoItemLongClicked(Video item);
    void onScrollEnd(Video item);
    boolean hasPendingActions();
}
