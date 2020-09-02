package com.liskovsoft.smartyoutubetv2.common.mvp.models.data;

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
    private Header mHeader;
    private boolean mIsContinued;

    public static VideoGroup from(MediaGroup mediaGroup, Header header) {
        VideoGroup videoGroup = new VideoGroup();
        videoGroup.mHeader = header;

        if (mediaGroup == null) {
            return videoGroup;
        }

        videoGroup.mMediaGroup = mediaGroup;
        videoGroup.mId = mediaGroup.hashCode(); // TODO: replace with real id
        videoGroup.mTitle = mediaGroup.getTitle();
        videoGroup.mVideos = new ArrayList<>();

        if (mediaGroup.getMediaItems() == null) {
            Log.e(TAG, "MediaGroup doesn't contain media items. Title: " + mediaGroup.getTitle());
            return videoGroup;
        }

        for (MediaItem item : mediaGroup.getMediaItems()) {
            long id = item.getId();
            String title = item.getTitle();
            String category = item.getContentType();
            String desc = item.getDescription();
            String videoId = item.getMediaId();
            String videoUrl = item.getMediaUrl();
            String bgImageUrl = item.getBackgroundImageUrl();
            String cardImageUrl = item.getCardImageUrl();
            String studio = item.getDescription();

            // Build a Video object to be processed.
            Video video = new Video.VideoBuilder()
                    .id(id)
                    .title(title)
                    .category(category)
                    .description(desc)
                    .videoId(videoId)
                    .videoUrl(videoUrl)
                    .bgImageUrl(bgImageUrl)
                    .cardImageUrl(cardImageUrl)
                    .studio(studio)
                    .build();

            videoGroup.mVideos.add(video);
        }

        return videoGroup;
    }

    public static VideoGroup from(Header header) {
        VideoGroup videoGroup = new VideoGroup();
        videoGroup.mHeader = header;

        return videoGroup;
    }

    public static VideoGroup from(MediaGroup mediaGroup) {
        return from(mediaGroup, null);
    }

    public List<Video> getVideos() {
        return mVideos;
    }

    public void setVideos(List<Video> videos) {
        mVideos = videos;
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

    public void setId(int id) {
        mId = id;
    }

    public MediaGroup getMediaGroup() {
        return mMediaGroup;
    }

    public void setMediaGroup(MediaGroup group) {
        mMediaGroup = group;
    }

    public Header getHeader() {
        return mHeader;
    }

    public boolean isEmpty() {
        return mVideos == null;
    }

    public boolean isContinued() {
        return mIsContinued;
    }

    public void setContinued(boolean continued) {
        mIsContinued = continued;
    }
}
