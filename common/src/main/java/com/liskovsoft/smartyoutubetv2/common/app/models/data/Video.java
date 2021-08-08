package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import android.media.MediaDescription;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;

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
    public String author;
    public String badge;
    public String previewUrl;
    public float percentWatched = -1;
    public MediaItem mediaItem; // memory leak
    public MediaItem nextMediaItem; // memory leak
    public VideoGroup group; // used to get next page when scrolling
    public boolean hasNewContent;
    public boolean isLive;
    public boolean isUpcoming;
    public boolean isSubscribed;
    public boolean isRemote;
    public int groupPosition = -1; // group position in multi-grid fragments
    public String clickTrackingParams;

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
            final String author) {
        this.id = id;
        this.category = category;
        this.title = title;
        this.description = desc;
        this.videoId = videoId;
        this.videoUrl = videoUrl;
        this.bgImageUrl = bgImageUrl;
        this.cardImageUrl = cardImageUrl;
        this.author = author;
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
        author = in.readString();
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
        video.author = item.getAuthor();
        video.percentWatched = item.getPercentWatched();
        video.badge = item.getBadgeText();
        video.hasNewContent = item.hasNewContent();
        video.previewUrl = item.getVideoPreviewUrl();
        video.playlistId = item.getPlaylistId();
        video.playlistIndex = item.getPlaylistIndex();
        video.isLive = item.isLive();
        video.isUpcoming = item.isUpcoming();
        video.clickTrackingParams = item.getClickTrackingParams();
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

    ///**
    // * Don't change the logic from equality by reference!<br/>
    // * Or adapters won't work properly (same video may appear twice).
    // */
    //@Override
    //public boolean equals(@Nullable Object obj) {
    //    return super.equals(obj);
    //}

    /**
     * Use with caution.<br/>
     * Old logic is equality by reference!<br/>
     * Adapters may not work properly when detecting scroll position (same video may appear twice).
     */
    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof Video) {
            if (videoId != null) {
                return videoId.equals(((Video) obj).videoId);
            }

            if (playlistId != null) {
                return playlistId.equals(((Video) obj).playlistId);
            }

            if (channelId != null) {
                return channelId.equals(((Video) obj).channelId);
            }
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Helpers.hashCode(title, description, videoId, playlistId, channelId);
    }
    
    public static boolean equals(Video video1, Video video2) {
        if (video1 == null) {
            return false;
        }

        return video1.equals(video2);
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
        dest.writeString(author);
    }

    public static Video fromString(String spec) {
        if (spec == null) {
            return null;
        }

        String[] split = spec.split("&vi;");

        if (split.length != 10) {
            return null;
        }

        Video result = new Video();

        result.id = Helpers.parseLong(split[0]);
        result.category = Helpers.parseStr(split[1]);
        result.title = Helpers.parseStr(split[2]);
        result.videoId = Helpers.parseStr(split[3]);
        result.videoUrl = Helpers.parseStr(split[4]);
        result.playlistId = Helpers.parseStr(split[5]);
        result.channelId = Helpers.parseStr(split[6]);
        result.bgImageUrl = Helpers.parseStr(split[7]);
        result.cardImageUrl = Helpers.parseStr(split[8]);
        result.mediaItem = YouTubeMediaService.deserializeMediaItem(Helpers.parseStr(split[9]));

        return result;
    }

    @Override
    public String toString() {
        return String.format("%s&vi;%s&vi;%s&vi;%s&vi;%s&vi;%s&vi;%s&vi;%s&vi;%s&vi;%s",
                id, category, title, videoId, videoUrl, playlistId, channelId, bgImageUrl, cardImageUrl, YouTubeMediaService.serialize(mediaItem));
    }

    //@Override
    //public String toString() {
    //    String s = "Video{";
    //    s += "id=" + id;
    //    s += ", category='" + category + "'";
    //    s += ", title='" + title + "'";
    //    s += ", videoId='" + videoId + "'";
    //    s += ", videoUrl='" + videoUrl + "'";
    //    s += ", bgImageUrl='" + bgImageUrl + "'";
    //    s += ", cardImageUrl='" + cardImageUrl + "'";
    //    s += ", studio='" + cardImageUrl + "'";
    //    s += "}";
    //    return s;
    //}

    public boolean isVideo() {
        return videoId != null;
    }

    public boolean isChannel() {
        return videoId == null && channelId != null;
    }

    public boolean isPlaylist() {
        return mediaItem != null && mediaItem.getType() == MediaItem.TYPE_PLAYLIST;
    }

    public boolean isPlaylistItem() {
        return playlistIndex > 0;
    }

    public boolean hasUploads() {
        return mediaItem != null && mediaItem.hasUploads();
    }

    public boolean isChannelUploadsSection() {
        return mediaItem != null && mediaItem.getType() == MediaItem.TYPE_CHANNELS_SECTION;
    }

    public boolean isPlaylistSection() {
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
