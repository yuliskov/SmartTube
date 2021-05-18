package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.util.ArrayList;
import java.util.List;

public class VideoGroup {
    private static final String TAG = VideoGroup.class.getSimpleName();
    private int mId;
    private String mTitle;
    private List<Video> mVideos;
    private MediaGroup mMediaGroup;
    private Category mCategory;

    public static VideoGroup from(Category category) {
        return from(null, category);
    }

    public static VideoGroup from(MediaGroup mediaGroup) {
        return from(mediaGroup, null);
    }

    public static VideoGroup from(MediaGroup mediaGroup, Category category) {
        VideoGroup videoGroup = new VideoGroup();
        videoGroup.mCategory = category;

        if (mediaGroup == null) {
            return videoGroup;
        }

        videoGroup.mMediaGroup = mediaGroup;
        videoGroup.mTitle = mediaGroup.getTitle();
        // TODO: replace with real id
        videoGroup.mId = mediaGroup.hashCode();
        videoGroup.mVideos = new ArrayList<>();

        if (mediaGroup.getMediaItems() == null) {
            Log.e(TAG, "MediaGroup doesn't contain media items. Title: " + mediaGroup.getTitle());
            return videoGroup;
        }

        for (MediaItem item : mediaGroup.getMediaItems()) {
            videoGroup.mVideos.add(Video.from(item));
        }

        return videoGroup;
    }

    public List<Video> getVideos() {
        return mVideos;
    }

    public String getTitle() {
        return mTitle;
    }

    public int getId() {
        return mId;
    }

    public MediaGroup getMediaGroup() {
        return mMediaGroup;
    }

    public Category getCategory() {
        return mCategory;
    }

    public boolean isEmpty() {
        return mVideos == null;
    }
}
