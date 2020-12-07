package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import android.media.MediaDescription;
import android.os.Parcel;
import android.os.Parcelable;
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
    public String bgImageUrl;
    public String cardImageUrl;
    public String studio;
    public String badge;
    public String previewUrl;
    public int playlistIndex;
    public int percentWatched = -1;
    public MediaItem mediaItem;
    public MediaItem nextMediaItem;
    public boolean hasNewContent;
    public boolean isLive;
    public boolean isUpcoming;

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
        video.playlistIndex = item.getPlaylistIndex();
        video.isLive = item.isLive();
        video.isUpcoming = item.isUpcoming();
        video.mediaItem = item;

        return video;
    }

    public static Video from(String videoId) {
        Video video = new Video();
        video.videoId = videoId;

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

    @Override
    public boolean equals(Object m) {
        if (m instanceof Video) {
            if (videoId != null) {
                return videoId.equals(((Video) m).videoId);
            }
        }

        return false;
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

    public boolean isChannelSection() {
        return mediaItem != null && mediaItem.getType() == MediaItem.TYPE_CHANNELS_SECTION;
    }

    public boolean isPlaylistItem() {
        return playlistIndex > 0;
    }

    public boolean isPlaylist() {
        return mediaItem != null && mediaItem.getType() == MediaItem.TYPE_PLAYLISTS_SECTION;
    }

    public void sync(MediaItemMetadata metadata, boolean useAlt) {
        if (metadata == null) {
            return;
        }

        title = metadata.getTitle();
        description = useAlt ? metadata.getDescriptionAlt() : metadata.getDescription();
        channelId = metadata.getChannelId();
        nextMediaItem = metadata.getNextVideo();
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
