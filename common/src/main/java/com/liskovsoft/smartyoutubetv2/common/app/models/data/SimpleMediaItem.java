package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;

public final class SimpleMediaItem implements MediaItem {
    private int mId;
    private String mVideoId;
    private String mPlaylistId;
    private String mTitle;
    private String mReloadPageKey;
    private String mChannelId;
    private String mCardImageUrl;
    private String mParams;
    private CharSequence mSecondTitle;
    private String mContentType;
    private int mType;
    private String mVideoUrl;
    private String mBackgroundImageUrl;
    private String mAuthor;
    private int mPercentWatched;
    private int mStartTimeSeconds;
    private String mBadgeText;
    private boolean mHaseNewContent;
    private String mVideoPreviewUrl;
    private int mPlaylistIndex;
    private boolean mIsLive;
    private boolean mIsUpcoming;
    private boolean mIsMovie;
    private String mClickTrackingParams;

    private SimpleMediaItem() {
    }

    public static MediaItem from(MediaItemMetadata metadata) {
        SimpleMediaItem mediaItem = new SimpleMediaItem();

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

    public static MediaItem from(Video video) {
        SimpleMediaItem mediaItem = new SimpleMediaItem();

        mediaItem.mId = video.id;
        mediaItem.mTitle = video.getTitle();
        mediaItem.mSecondTitle = video.getSecondTitle();
        mediaItem.mContentType = video.category;
        mediaItem.mType = video.itemType;
        mediaItem.mVideoId = video.videoId;
        mediaItem.mChannelId = video.channelId;
        mediaItem.mVideoUrl = video.videoUrl;
        mediaItem.mBackgroundImageUrl = video.bgImageUrl;
        mediaItem.mCardImageUrl = video.cardImageUrl;
        mediaItem.mAuthor = video.author;
        mediaItem.mPercentWatched = (int) video.percentWatched;
        mediaItem.mStartTimeSeconds = video.startTimeSeconds;
        mediaItem.mBadgeText = video.badge;
        mediaItem.mHaseNewContent = video.hasNewContent;
        mediaItem.mVideoPreviewUrl = video.previewUrl;
        mediaItem.mPlaylistId = video.playlistId;
        mediaItem.mPlaylistIndex = video.playlistIndex;
        mediaItem.mParams = video.playlistParams;
        mediaItem.mReloadPageKey = video.reloadPageKey;
        mediaItem.mIsLive = video.isLive;
        mediaItem.mIsUpcoming = video.isUpcoming;
        mediaItem.mIsMovie = video.isMovie;
        mediaItem.mClickTrackingParams = video.clickTrackingParams;

        return mediaItem;
    }

    @Override
    public int getType() {
        return mType;
    }

    @Override
    public boolean isLive() {
        return mIsLive;
    }

    @Override
    public boolean isUpcoming() {
        return mIsUpcoming;
    }

    @Override
    public boolean isShorts() {
        return false;
    }

    @Override
    public boolean isMovie() {
        return mIsMovie;
    }

    @Override
    public int getPercentWatched() {
        return mPercentWatched;
    }

    @Override
    public int getStartTimeSeconds() {
        return mStartTimeSeconds;
    }

    @Override
    public String getAuthor() {
        return mAuthor;
    }

    @Override
    public String getFeedbackToken() {
        return null;
    }

    @Override
    public String getFeedbackToken2() {
        return null;
    }

    @Override
    public String getPlaylistId() {
        return mPlaylistId;
    }

    @Override
    public int getPlaylistIndex() {
        return mPlaylistIndex;
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
        return mHaseNewContent;
    }

    @Override
    public int getId() {
        return mId;
    }

    @Override
    public String getTitle() {
        return mTitle;
    }

    @Override
    public CharSequence getSecondTitle() {
        return mSecondTitle;
    }

    @Override
    public String getVideoUrl() {
        return mVideoUrl;
    }

    @Override
    public String getVideoId() {
        return mVideoId;
    }

    @Override
    public String getContentType() {
        return mContentType;
    }

    @Override
    public long getDurationMs() {
        return 0;
    }

    @Override
    public String getBadgeText() {
        return mBadgeText;
    }

    @Override
    public String getProductionDate() {
        return null;
    }

    @Override
    public long getPublishedDate() {
        return 0;
    }

    @Override
    public String getCardImageUrl() {
        return mCardImageUrl;
    }

    @Override
    public String getBackgroundImageUrl() {
        return mBackgroundImageUrl;
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
        return mVideoPreviewUrl;
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
        return mClickTrackingParams;
    }

    @Override
    public void sync(MediaItemMetadata metadata) {

    }
}
