package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import android.media.MediaDescription;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;

/**
 * Video is an object that holds the various metadata associated with a single video.
 */
public final class Video implements Parcelable {
    public long id;
    public String title;
    public String category;
    public String description;
    public String channelId;
    public String videoId;
    public String videoUrl;
    public String playlistId;
    public int playlistIndex;
    public String bgImageUrl;
    public String cardImageUrl;
    public String studio;
    public String badge;
    public String previewUrl;
    public int percentWatched = -1;
    public MediaItem mediaItem; // memory leak
    public MediaItem nextMediaItem; // memory leak
    public VideoGroup group; // used to get next page when scrolling
    public boolean hasNewContent;
    public boolean isLive;
    public boolean isUpcoming;
    public boolean isSubscribed;
    public boolean isRemote;
    public int groupPosition = -1; // group position in multi-grid fragments

    public Video() {
        
    }

    private Video(
            final long id,
            final String category,
            final String title,
            final String desc,
            final String videoId,
            final String videoUrl,
            final String bgImageUrl,
            final String cardImageUrl,
            final String studio) {
        this.id = id;
        this.category = category;
        this.title = title;
        this.description = desc;
        this.videoId = videoId;
        this.videoUrl = videoUrl;
        this.bgImageUrl = bgImageUrl;
        this.cardImageUrl = cardImageUrl;
        this.studio = studio;
    }

    protected Video(Parcel in) {
        id = in.readLong();
        category = in.readString();
        title = in.readString();
        description = in.readString();
        bgImageUrl = in.readString();
        cardImageUrl = in.readString();
        videoId = in.readString();
        videoUrl = in.readString();
        studio = in.readString();
    }

    public static Video from(MediaItem item) {
        Video video = new Video();

        video.id = item.getId();
        video.title = item.getTitle();
        video.category = item.getContentType();
        video.description = item.getDescription();
        video.videoId = item.getVideoId();
        video.channelId = item.getChannelId();
        video.videoUrl = item.getVideoUrl();
        video.bgImageUrl = item.getBackgroundImageUrl();
        video.cardImageUrl = item.getCardImageUrl();
        video.studio = item.getAuthor();
        video.percentWatched = item.getPercentWatched();
        video.badge = item.getBadgeText();
        video.hasNewContent = item.hasNewContent();
        video.previewUrl = item.getVideoPreviewUrl();
        video.playlistId = item.getPlaylistId();
        video.playlistIndex = item.getPlaylistIndex();
        video.isLive = item.isLive();
        video.isUpcoming = item.isUpcoming();
        video.mediaItem = item;

        return video;
    }

    public static Video from(String videoId) {
        return from(videoId, null, -1);
    }

    public static Video from(String videoId, String playlistId, int playlistIndex) {
        Video video = new Video();
        video.videoId = videoId;
        video.playlistId = playlistId;
        video.playlistIndex = playlistIndex;

        return video;
    }

    public static final Creator<Video> CREATOR = new Creator<Video>() {
        @Override
        public Video createFromParcel(Parcel in) {
            return new Video(in);
        }

        @Override
        public Video[] newArray(int size) {
            return new Video[size];
        }
    };

    /**
     * Don't change the logic from equality by reference!<br/>
     * Or adapters won't work properly.
     */
    @Override
    public boolean equals(@Nullable Object obj) {
        return super.equals(obj);
    }

    /**
     * Equality that intended for playlists or other not strong cases.
     */
    public static boolean equals(Video video1, Video video2) {
        if (video1 == null || video2 == null) {
            return false;
        }

        if (video1.videoId == null) {
            return false;
        }

        return video1.videoId.equals(video2.videoId);
    }

    public static boolean isEmpty(Video video) {
        return video == null || video.videoId == null;
    }

    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(category);
        dest.writeString(title);
        dest.writeString(description);
        dest.writeString(bgImageUrl);
        dest.writeString(cardImageUrl);
        dest.writeString(videoId);
        dest.writeString(videoUrl);
        dest.writeString(studio);
    }

    @Override
    public String toString() {
        String s = "Video{";
        s += "id=" + id;
        s += ", category='" + category + "'";
        s += ", title='" + title + "'";
        s += ", videoId='" + videoId + "'";
        s += ", videoUrl='" + videoUrl + "'";
        s += ", bgImageUrl='" + bgImageUrl + "'";
        s += ", cardImageUrl='" + cardImageUrl + "'";
        s += ", studio='" + cardImageUrl + "'";
        s += "}";
        return s;
    }

    public boolean isVideo() {
        return videoId != null;
    }

    public boolean isChannel() {
        return videoId == null && channelId != null;
    }

    public boolean isChannelUploads() {
        return mediaItem != null && mediaItem.getType() == MediaItem.TYPE_CHANNELS_SECTION;
    }

    public boolean isPlaylistItem() {
        return playlistIndex > 0;
    }

    public boolean isPlaylist() {
        return mediaItem != null && mediaItem.getType() == MediaItem.TYPE_PLAYLISTS_SECTION;
    }

    public void sync(MediaItemMetadata metadata, boolean useAltDesc) {
        if (metadata == null) {
            return;
        }

        title = metadata.getTitle();
        // Don't sync future translation because of not precise description
        if (!metadata.isUpcoming()) {
            description = useAltDesc ? metadata.getDescriptionAlt() : metadata.getDescription();
        }
        channelId = metadata.getChannelId();
        nextMediaItem = metadata.getNextVideo();
        isLive = metadata.isLive();
        isSubscribed = metadata.isSubscribed();
        isUpcoming = metadata.isUpcoming();
    }

    // Builder for Video object.
    public static class VideoBuilder {
        private long id;
        private String category;
        private String title;
        private String desc;
        private String bgImageUrl;
        private String cardImageUrl;
        private String videoId;
        private String videoUrl;
        private String studio;
        private MediaItem mediaItem;
        private MediaItemMetadata mediaItemMetadata;

        public VideoBuilder id(long id) {
            this.id = id;
            return this;
        }

        public VideoBuilder category(String category) {
            this.category = category;
            return this;
        }

        public VideoBuilder title(String title) {
            this.title = title;
            return this;
        }

        public VideoBuilder description(String desc) {
            this.desc = desc;
            return this;
        }

        public VideoBuilder videoId(String videoId) {
            this.videoId = videoId;
            return this;
        }

        public VideoBuilder videoUrl(String videoUrl) {
            this.videoUrl = videoUrl;
            return this;
        }

        public VideoBuilder bgImageUrl(String bgImageUrl) {
            this.bgImageUrl = bgImageUrl;
            return this;
        }

        public VideoBuilder cardImageUrl(String cardImageUrl) {
            this.cardImageUrl = cardImageUrl;
            return this;
        }

        public VideoBuilder studio(String studio) {
            this.studio = studio;
            return this;
        }

        @RequiresApi(21)
        public Video buildFromMediaDesc(MediaDescription desc) {
            return new Video(
                    Long.parseLong(desc.getMediaId()),
                    "", // Category - not provided by MediaDescription.
                    String.valueOf(desc.getTitle()),
                    String.valueOf(desc.getDescription()),
                    "", // Media ID - not provided by MediaDescription.
                    "", // Media URI - not provided by MediaDescription.
                    "", // Background Image URI - not provided by MediaDescription.
                    String.valueOf(desc.getIconUri()),
                    String.valueOf(desc.getSubtitle())
            );
        }

        public Video build() {
            return new Video(
                    id,
                    category,
                    title,
                    desc,
                    videoId,
                    videoUrl,
                    bgImageUrl,
                    cardImageUrl,
                    studio
            );
        }
    }
}
