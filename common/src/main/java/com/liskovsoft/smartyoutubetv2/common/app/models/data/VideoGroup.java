package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.util.ArrayList;
import java.util.List;

public class VideoGroup {
    public static final int ACTION_APPEND = 0;
    public static final int ACTION_REPLACE = 1;
    public static final int ACTION_REMOVE = 2;
    private static final String TAG = VideoGroup.class.getSimpleName();
    private int mId;
    private String mTitle;
    private List<Video> mVideos;
    private MediaGroup mMediaGroup;
    private BrowseSection mCategory;
    private int mPosition;
    private int mAction;

    public static VideoGroup from(BrowseSection category) {
        return from(null, category);
    }

    public static VideoGroup from(MediaGroup mediaGroup) {
        return from(mediaGroup, null);
    }

    public static VideoGroup from(BrowseSection category, int groupPosition) {
        return from(null, category, groupPosition);
    }

    public static VideoGroup from(MediaGroup mediaGroup, BrowseSection category) {
        return from(mediaGroup, category, -1);
    }

    public static VideoGroup from(MediaGroup mediaGroup, BrowseSection category, int groupPosition) {
        VideoGroup videoGroup = new VideoGroup();
        videoGroup.mCategory = category;
        videoGroup.mPosition = groupPosition;

        if (mediaGroup == null) {
            return videoGroup;
        }

        videoGroup.mMediaGroup = mediaGroup;
        videoGroup.mTitle = mediaGroup.getTitle();
        videoGroup.mId = mediaGroup.getId();
        videoGroup.mVideos = new ArrayList<>();

        if (mediaGroup.getMediaItems() == null) {
            Log.e(TAG, "MediaGroup doesn't contain media items. Title: " + mediaGroup.getTitle());
            return videoGroup;
        }

        for (MediaItem item : mediaGroup.getMediaItems()) {
            Video video = Video.from(item);
            // Group position in multi-grid fragments
            video.groupPosition = videoGroup.mPosition;
            video.group = videoGroup;
            videoGroup.mVideos.add(video);
        }

        return videoGroup;
    }

    public List<Video> getVideos() {
        return mVideos;
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String title) {
        mTitle = title;
    }

    public int getId() {
        return mId;
    }

    public MediaGroup getMediaGroup() {
        return mMediaGroup;
    }

    public BrowseSection getSection() {
        return mCategory;
    }

    public boolean isEmpty() {
        return mVideos == null || mVideos.isEmpty();
    }

    /**
     * Group position in multi-grid fragments<br/>
     * It isn't used on other types of fragments.
     */
    public int getPosition() {
        return mPosition;
    }

    public int getAction() {
        return mAction;
    }

    public void setAction(int action) {
        mAction = action;
    }
}
