package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;

public class ChannelPresenter implements VideoGroupPresenter<ChannelView> {
    @SuppressLint("StaticFieldLeak")
    private static ChannelPresenter sInstance;
    private final Context mContext;

    public ChannelPresenter(Context context) {
        mContext = context;
    }

    public static ChannelPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new ChannelPresenter(context.getApplicationContext());
        }

        return sInstance;
    }

    @Override
    public void onInitDone() {

    }

    @Override
    public void onVideoItemClicked(Video item) {

    }

    @Override
    public void onVideoItemLongClicked(Video item) {

    }

    @Override
    public void onScrollEnd(VideoGroup group) {

    }

    @Override
    public void register(ChannelView view) {

    }

    @Override
    public void unregister(ChannelView view) {

    }
}
