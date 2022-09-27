package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;

public final class SampleMediaItem implements MediaItem {
    private String mVideoId;
    private String mPlaylistId;
    private String mTitle;
    private String mReloadPageKey;
    private String mChannelId;
    private String mCardImageUrl;
    private String mParams;
    private String mSecondTitle;

    private SampleMediaItem() {
    }

    public static MediaItem from(MediaItemMetadata metadata) {
        SampleMediaItem mediaItem = new SampleMediaItem();

        mediaItem.mTitle = metadata.getTitle();
        mediaItem.mSecondTitle = metadata.getSecondTitle();
        mediaItem.mVideoId = metadata.getVideoId();
        mediaItem.mPlaylistId = metadata.getPlaylistInfo() != null ?
                metadata.getPlaylistInfo().getPlaylistId() : metadata.getNextVideo() != null ?
                metadata.getNextVideo().getPlaylistId() : null;
        mediaItem.mParams = metadata.getParams();
        mediaItem.mChannelId = metadata.getChannelId();

        return mediaItem;
    }

    @Override
    public int getType() {
        return 0;
    }

    @Override
    public boolean isLive() {
        return false;
    }

    @Override
    public boolean isUpcoming() {
        return false;
    }

    @Override
    public int getPercentWatched() {
        return 0;
    }

    @Override
    public String getAuthor() {
        return null;
    }

    @Override
    public String getFeedbackToken() {
        return null;
    }

    @Override
    public String getPlaylistId() {
        return mPlaylistId;
    }

    @Override
    public int getPlaylistIndex() {
        return 0;
    }

    @Override
    public String getParams() {
        return mParams;
    }

    @Override
    public String getReloadPageKey() {
        return mReloadPageKey;
    }

    @Override
    public boolean hasNewContent() {
        return false;
    }

    @Override
    public int getId() {
        return 0;
    }

    @Override
    public String getTitle() {
        return mTitle;
    }

    @Override
    public String getSecondTitle() {
        return mSecondTitle;
    }

    @Override
    public String getVideoUrl() {
        return null;
    }

    @Override
    public String getVideoId() {
        return mVideoId;
    }

    @Override
    public String getContentType() {
        return null;
    }

    @Override
    public int getDurationMs() {
        return 0;
    }

    @Override
    public String getBadgeText() {
        return null;
    }

    @Override
    public String getProductionDate() {
        return null;
    }

    @Override
    public String getCardImageUrl() {
        return mCardImageUrl;
    }

    @Override
    public String getBackgroundImageUrl() {
        return null;
    }

    @Override
    public int getWidth() {
        return 0;
    }

    @Override
    public int getHeight() {
        return 0;
    }

    @Override
    public String getChannelId() {
        return mChannelId;
    }

    @Override
    public String getChannelUrl() {
        return null;
    }

    @Override
    public String getVideoPreviewUrl() {
        return null;
    }

    @Override
    public String getAudioChannelConfig() {
        return null;
    }

    @Override
    public String getPurchasePrice() {
        return null;
    }

    @Override
    public String getRentalPrice() {
        return null;
    }

    @Override
    public int getRatingStyle() {
        return 0;
    }

    @Override
    public double getRatingScore() {
        return 0;
    }

    @Override
    public boolean hasUploads() {
        return false;
    }

    @Override
    public String getClickTrackingParams() {
        return null;
    }

    @Override
    public void sync(MediaItemMetadata metadata) {

    }
}
