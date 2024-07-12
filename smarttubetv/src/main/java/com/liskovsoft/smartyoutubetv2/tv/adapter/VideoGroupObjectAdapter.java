package com.liskovsoft.smartyoutubetv2.tv.adapter;

import androidx.annotation.NonNull;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class VideoGroupObjectAdapter extends ObjectAdapter {
    private static final String TAG = VideoGroupObjectAdapter.class.getSimpleName();
    private final List<Video> mVideoItems = new ArrayList<Video>() {
        @Override
        public boolean addAll(@NonNull Collection<? extends Video> c) {
            // TODO: remove the hack someday.
            // Dirty hack for avoiding group duplication.
            // Duplicated items suddenly appeared in Home, Subscriptions and History.

            // Another alt method.
            if (size() > 0 && size() < CHECK_MAX_SIZE) {
                Helpers.removeIf(c, this::contains);
            }

            return super.addAll(c);
        }
    };
    private final List<VideoGroup> mVideoGroups = new ArrayList<>(); // keep groups from being garbage collected
    private static final int CHECK_MAX_SIZE = 200;

    public VideoGroupObjectAdapter(VideoGroup videoGroup, Presenter presenter) {
        super(presenter);

        initData(videoGroup);
    }

    public VideoGroupObjectAdapter(VideoGroup videoGroup, PresenterSelector presenter) {
        super(presenter);

        initData(videoGroup);
    }

    public VideoGroupObjectAdapter(Presenter presenter) {
        this(null, presenter);
    }

    public VideoGroupObjectAdapter(PresenterSelector presenter) {
        this(null, presenter);
    }

    private void initData(VideoGroup videoGroup) {
        if (videoGroup != null) {
            add(videoGroup);
        }
    }

    @Override
    public int size() {
        return mVideoItems.size();
    }

    @Override
    public Object get(int index) {
        if (index < 0 || index >= mVideoItems.size()) {
            return null;
        }

        return mVideoItems.get(index);
    }

    public List<Video> getAll() {
        return mVideoItems;
    }

    public List<VideoGroup> getAllGroups() {
        return mVideoGroups;
    }

    public void add(VideoGroup group) {
        if (group == null || group.getVideos() == null) {
            return;
        }

        if (group.getAction() == VideoGroup.ACTION_PREPEND) {
            prepend(group); // add at the begin of the existing group
        } else {
            append(group); // add at the end of the the existing group
        }
    }

    public void add(List<Video> videos) {
        if (videos == null || videos.isEmpty()) {
            return;
        }

        add(VideoGroup.from(videos));
    }

    private void prepend(VideoGroup group) {
        int begin = mVideoItems.size();

        if (mVideoGroups.contains(group)) {
            mVideoItems.addAll(0, group.getVideos().subList(begin, group.getVideos().size()));
        } else {
            mVideoItems.addAll(0, group.getVideos());
            mVideoGroups.add(0, group);
        }

        // Fix double item blinking by specifying exact range
        notifyItemRangeInserted(0, mVideoItems.size() - begin);
    }

    private void append(VideoGroup group) {
        int begin = mVideoItems.size();

        if (mVideoGroups.contains(group)) {
            mVideoItems.addAll(group.getVideos().subList(begin, group.getVideos().size()));
        } else {
            mVideoItems.addAll(group.getVideos());
            mVideoGroups.add(group);
        }

        // Fix double item blinking by specifying exact range
        notifyItemRangeInserted(begin, mVideoItems.size() - begin);
    }

    /**
     * Compare by reference. Because there may be multiple same videos.
     */
    public int indexOf(Video item) {
        // Compare by reference. Because there may be multiple same videos.
        int index = -1;

        for (Video video : mVideoItems) {
            index++;
            if (video == item) {
                return index;
            }
        }

        return -1;
    }

    /**
     * Regular compare. Use with caution!<br/>
     * UI may consists of multiple rows with same video or even multiple videos in the same row.
     */
    public int indexOfAlt(Video item) {
        int index = -1;

        for (Video video : mVideoItems) {
            index++;
            if (video.equals(item)) {
                return index;
            }
        }

        return -1;
    }

    public void clear() {
        int itemCount = mVideoItems.size();
        mVideoItems.clear();
        mVideoGroups.clear();
        if (itemCount != 0) {
            notifyItemRangeRemoved(0, itemCount);
        }
    }

    public void remove(VideoGroup group) {
        for (Video video : group.getVideos()) {
            while (true) { // remove all occurrences of the same element (if present)
                int index = mVideoItems.indexOf(video);
                if (index != -1) {
                    mVideoItems.remove(video);
                    notifyItemRangeRemoved(index, 1);
                    removeFromGroup(video);
                } else {
                    break;
                }
            }
        }
    }

    public void removeAuthor(VideoGroup group) {
        String author = group.getVideos().get(0).getAuthor(); // assume same author
        List<Video> result = Helpers.filter(mVideoItems, video -> Helpers.equals(author, video.getAuthor()));
        if (result != null) {
            remove(VideoGroup.from(result));
        }
    }

    public void sync(VideoGroup group) {
        for (Video video : group.getVideos()) {
            // Search for multiple occurrences (e.g. History section)
            for (int i = 0; i < mVideoItems.size(); i++) {
                Video origin = mVideoItems.get(i);
                if (origin.equals(video)) {
                    origin.sync(video);
                    notifyItemRangeChanged(i, 1);
                }
            }
        }
    }

    public boolean isEmpty() {
        return mVideoItems.isEmpty();
    }

    private void removeFromGroup(Video video) {
        if (video != null && video.getGroup() != null) {
            video.getGroup().remove(video);
        }
    }
}
