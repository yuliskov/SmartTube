package com.liskovsoft.smartyoutubetv2.common.mvp.models;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemSuggestions;

import java.util.ArrayList;
import java.util.List;

public class VideoGroup {
    private int mId;
    private String mTitle;
    private List<Video> mVideos;

    public static VideoGroup from(MediaGroup mediaGroup) {
        VideoGroup videoGroup = new VideoGroup();
        videoGroup.mId = mediaGroup.hashCode(); // TODO: replace with real id
        videoGroup.mTitle = mediaGroup.getTitle();
        videoGroup.mVideos = new ArrayList<>();

        for (MediaItem item : mediaGroup.getMediaItems()) {
            long id = item.getId();
            String title = item.getTitle();
            String category = item.getContentType();
            String desc = item.getDescription();
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
                    .videoUrl(videoUrl)
                    .bgImageUrl(bgImageUrl)
                    .cardImageUrl(cardImageUrl)
                    .studio(studio)
                    .build();

            videoGroup.mVideos.add(video);
        }

        return videoGroup;
    }

    public static VideoGroup from(MediaItemSuggestions suggestions) {
        return null;
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
}
