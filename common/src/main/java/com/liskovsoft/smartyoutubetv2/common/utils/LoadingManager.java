package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelUploadsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;

public class LoadingManager {
    public static void showLoading(Context context, boolean show, Class<?> view) {
        Class<?> topView = ViewManager.instance(context).getTopView();
        if (topView == view) {
            showLoading(context, show);
        }
    }

    public static void showLoading(Context context, boolean show) {
        Class<?> topView = ViewManager.instance(context).getTopView();

        if (topView == BrowseView.class) {
            BrowseView browseView = BrowsePresenter.instance(context).getView();
            if (browseView != null) {
                browseView.showProgressBar(show);
            }
        } else if (topView == SearchView.class) {
            SearchView searchView = SearchPresenter.instance(context).getView();
            if (searchView != null) {
                searchView.showProgressBar(show);
            }
        } else if (topView == ChannelView.class) {
            ChannelView channelView = ChannelPresenter.instance(context).getView();
            if (channelView != null) {
                channelView.showProgressBar(show);
            }
        } else if (topView == ChannelUploadsView.class) {
            ChannelUploadsView uploadsView = ChannelUploadsPresenter.instance(context).getView();
            if (uploadsView != null) {
                uploadsView.showProgressBar(show);
            }
        } else if (topView == PlaybackView.class) {
            PlaybackView playbackView = PlaybackPresenter.instance(context).getView();
            if (playbackView != null) {
                playbackView.showProgressBar(show);
            }
        }
    }
}
