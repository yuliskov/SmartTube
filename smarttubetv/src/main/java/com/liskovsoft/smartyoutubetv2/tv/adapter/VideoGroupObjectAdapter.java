package com.liskovsoft.smartyoutubetv2.tv.adapter;

import androidx.leanback.widget.ObjectAdapter;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.tv.presenter.CardPresenter;

import java.util.ArrayList;
import java.util.List;

public class VideoGroupObjectAdapter extends ObjectAdapter {
    private static final String TAG = VideoGroupObjectAdapter.class.getSimpleName();
    private final List<Video> mVideoItems;

    // TODO: Select presenter based on the video item type. Such channel, playlist, or simple video
    // https://github.com/googlearchive/leanback-showcase/blob/master/app/src/main/java/android/support/v17/leanback/supportleanbackshowcase/app/page/PageAndListRowFragment.java
    // CardPresenterSelector cardPresenter = new CardPresenterSelector(getActivity());
    public VideoGroupObjectAdapter(VideoGroup videoGroup, CardPresenter presenter) {
        super(presenter);
        mVideoItems = new ArrayList<>();

        if (videoGroup != null) {
            append(videoGroup);
        }
    }

    public VideoGroupObjectAdapter(CardPresenter presenter) {
        this(null, presenter);
    }

    @Override
    public int size() {
        return mVideoItems.size();
    }

    @Override
    public Object get(int index) {
        if (index < 0 || index >= size()) {
            return null;
        }

        return mVideoItems.get(index);
    }

    public void append(VideoGroup group) {
        if (group != null && group.getVideos() != null) {
            int begin = mVideoItems.size();

            mVideoItems.addAll(group.getVideos());

            // Fix double item blinking by specifying exact range
            notifyItemRangeInserted(begin, mVideoItems.size() - begin);
        }
    }

    public int indexOf(Video item) {
        return mVideoItems.indexOf(item);
    }

    public void clear() {
        int itemCount = mVideoItems.size();
        mVideoItems.clear();
        if (itemCount != 0) {
            notifyItemRangeRemoved(0, itemCount);
        }
    }
}
