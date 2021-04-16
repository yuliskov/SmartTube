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
    private int mPosition;
    private boolean mIsBegin;

    public static VideoGroup from(Category category) {
        return from(null, category);
    }

    public static VideoGroup from(Category category, boolean isBegin) {
        return from(null, category, -1, isBegin);
    }

    public static VideoGroup from(Category category, int groupPosition, boolean isBegin) {
        return from(null, category, groupPosition, isBegin);
    }

    public static VideoGroup from(MediaGroup mediaGroup) {
        return from(mediaGroup, null);
    }

    public static VideoGroup from(MediaGroup mediaGroup, Category category) {
        return from(mediaGroup, category, -1);
    }

    public static VideoGroup from(MediaGroup mediaGroup, Category category, boolean isBegin) {
        return from(mediaGroup, category, -1, isBegin);
    }

    public static VideoGroup from(MediaGroup mediaGroup, Category category, int groupPosition) {
        return from(mediaGroup, category, groupPosition, false);
    }

    public static VideoGroup from(MediaGroup mediaGroup, Category category, int groupPosition, boolean isBegin) {
        VideoGroup videoGroup = new VideoGroup();
        videoGroup.mCategory = category;
        videoGroup.mPosition = groupPosition;
        videoGroup.mIsBegin = isBegin;

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
            Video video = Video.from(item);
            // Group position in multi-grid fragments
            video.groupPosition = videoGroup.mPosition;
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

    /**
     * Group position in multi-grid fragments<br/>
     * It isn't used on other types of fragments.
     */
    public int getPosition() {
        return mPosition;
    }

    public boolean isBegin() {
        return mIsBegin;
    }
}
