package com.liskovsoft.smartyoutubetv2.tv.adapter;

import androidx.annotation.NonNull;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.Presenter;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class VideoGroupObjectAdapter extends ObjectAdapter {
    private static final String TAG = VideoGroupObjectAdapter.class.getSimpleName();
    private final List<Video> mVideoItems;
    private final List<VideoGroup> mVideoGroups = new ArrayList<>(); // keep groups from being garbage collected

    public VideoGroupObjectAdapter(VideoGroup videoGroup, Presenter presenter) {
        super(presenter);
        mVideoItems = new ArrayList<Video>() {
            @Override
            public boolean addAll(@NonNull Collection<? extends Video> c) {
                // TODO: remove the hack someday.
                // Dirty hack for avoiding group duplication.
                // Duplicated items suddenly appeared in Home and Subscriptions.

                //if (size() >= c.size() && c.contains(get(c.size() - 1))) {
                //    return false;
                //}

                // Alt method. Works with Home rows.
                //if (size() < 30) {
                //    Video firstItem = Helpers.get(c, 0);
                //    if (firstItem != null && contains(firstItem)) {
                //        return false;
                //    }
                //}

                // Another alt method.
                if (size() > 0 && size() < 30) {
                    Helpers.removeIf(c, this::contains);
                }

                // Latest alt dubs fix method (not works).
                //Set<Video> uniqueItems = new LinkedHashSet<>(c);

                //return super.addAll(uniqueItems);

                return super.addAll(c);
            }
        };

        if (videoGroup != null) {
            append(videoGroup);
        }
    }

    public VideoGroupObjectAdapter(Presenter presenter) {
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

    public List<Video> getAll() {
        return mVideoItems;
    }

    public void append(VideoGroup group) {
        if (group != null && group.getVideos() != null) {
            int begin = mVideoItems.size();

            //mVideoItems.addAll(group.getVideos());
            //mVideoGroups.add(group);

            // The group now is expandable
            mVideoItems.addAll(group.getVideos().subList(begin, group.getVideos().size()));
            if (!mVideoGroups.contains(group)) {
                mVideoGroups.add(group);
            }

            // Fix double item blinking by specifying exact range
            notifyItemRangeInserted(begin, mVideoItems.size() - begin);
        }
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
        return mVideoItems == null || mVideoItems.isEmpty();
    }
}
