package com.liskovsoft.smartyoutubetv2.tv.adapter;

import androidx.leanback.widget.ObjectAdapter;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.Video;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.VideoGroup;
import com.liskovsoft.smartyoutubetv2.tv.presenter.CardPresenter;

import java.util.List;

public class VideoGroupObjectAdapter extends ObjectAdapter {
    private static final String TAG = VideoGroupObjectAdapter.class.getSimpleName();
    private final List<Video> mMediaItems;
    private final VideoGroup mMediaGroup;

    public VideoGroupObjectAdapter(VideoGroup videoGroup) {
        super(new CardPresenter());
        mMediaGroup = videoGroup;
        mMediaItems = videoGroup.getVideos();
    }

    @Override
    public int size() {
        return mMediaItems.size();
    }

    @Override
    public Object get(int position) {
        return mMediaItems.get(position);
    }

    public void append(VideoGroup mediaTab) {
        if (mMediaItems != null && mediaTab != null) {
            mMediaItems.addAll(mediaTab.getVideos());
        }
    }
}
