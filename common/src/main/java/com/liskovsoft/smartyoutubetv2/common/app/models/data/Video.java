package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import android.media.MediaDescription;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;

import java.util.ArrayList;
import java.util.List;

/**
 * Video is an object that holds the various metadata associated with a single video.
 */
public final class Video implements Parcelable {
    public static final String TERTIARY_TEXT_DELIM = "•";
    private static final int MAX_AUTHOR_LENGTH_CHARS = 20;
    private static final String[] sNotPlaylistParams = new String[] {"EAIYAQ%3D%3D"};
    private static final String SECTION_PREFIX = "FE";
    public long id;
    public String title;
    public String secondTitle;
    private String newTitle;
    private String newSecondTitle;
    public String description;
    public String category;
    public int itemType = -1;
    public String channelId;
    public String videoId;
    public String videoUrl;
    public String playlistId;
    public int playlistIndex;
    public String playlistParams;
    public String reloadPageKey;
    public String bgImageUrl;
    public String cardImageUrl;
    public String author;
    public String badge;
    public String previewUrl;
    public float percentWatched = -1;
    public MediaItem mediaItem;
    public MediaItem nextMediaItem;
    public VideoGroup group; // Memory leak. Used to get next page when scrolling.
    public boolean hasNewContent;
    public boolean isLive;
    public boolean isUpcoming;
    public boolean isSubscribed;
    public boolean isRemote;
    public int groupPosition = -1; // group position in multi-grid fragments
    public String clickTrackingParams;
    public boolean isSynced;
    public final long timestamp = System.currentTimeMillis();
    public int extra = -1;
    public int pendingPosMs;

    public Video() {
       // NOP
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
        this.secondTitle = desc;
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
        secondTitle = in.readString();
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
        video.secondTitle = item.getSecondTitle();
        video.category = item.getContentType();
        video.itemType = item.getType();
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
        video.playlistParams = item.getPlaylistParams();
        video.reloadPageKey = item.getReloadPageKey();
        video.isLive = item.isLive();
        video.isUpcoming = item.isUpcoming();
        video.clickTrackingParams = item.getClickTrackingParams();
        video.mediaItem = item;

        return video;
    }

    public static Video from(Video item) {
        Video video = new Video();

        video.id = item.id;
        video.title = item.title;
        video.category = item.category;
        video.itemType = item.itemType;
        video.secondTitle = item.secondTitle;
        video.videoId = item.videoId;
        video.channelId = item.channelId;
        video.videoUrl = item.videoUrl;
        video.bgImageUrl = item.bgImageUrl;
        video.cardImageUrl = item.cardImageUrl;
        video.author = item.author;
        video.percentWatched = item.percentWatched;
        video.badge = item.badge;
        video.hasNewContent = item.hasNewContent;
        video.previewUrl = item.previewUrl;
        video.playlistId = item.playlistId;
        video.playlistIndex = item.playlistIndex;
        video.playlistParams = item.playlistParams;
        video.reloadPageKey = item.reloadPageKey;
        video.isLive = item.isLive;
        video.isUpcoming = item.isUpcoming;
        video.clickTrackingParams = item.clickTrackingParams;
        video.mediaItem = item.mediaItem;

        return video;
    }

    public static Video from(String videoId) {
        return from(videoId, null, -1);
    }

    public static Video from(String videoId, String playlistId, int playlistIndex) {
        return from(videoId, playlistId, playlistIndex, null, null, null, -1);
    }

    public static Video from(String videoId, String playlistId, int playlistIndex, String channelId, String title, String info, float percentWatched) {
        Video video = new Video();
        video.videoId = videoId;
        video.playlistId = playlistId;
        video.playlistIndex = playlistIndex;
        video.channelId = channelId;
        video.title = title;
        video.secondTitle = info;
        video.percentWatched = percentWatched;

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
            Video video = (Video) obj;

            return hashCode() == video.hashCode();
        }

        return false;
    }

    /**
     * NOTE: hashCode is used generate id that should be the same if contents of items is the same
     */
    @Override
    public int hashCode() {
        int hashCode = Helpers.hashCodeAny(videoId, playlistId, reloadPageKey, playlistParams, channelId, mediaItem, extra);
        return hashCode != -1 ? hashCode : super.hashCode();
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

    public String extractAuthor() {
        if (author != null) {
            return author;
        }

        return extractAuthor(secondTitle);
    }

    private static String extractAuthor(String info) {
        String result = null;

        if (info != null) {
            info = info.replace(TERTIARY_TEXT_DELIM + " LIVE", ""); // remove special marks
            String[] split = info.split(TERTIARY_TEXT_DELIM);

            if (split.length <= 1) {
                result = info;
            } else {
                // First part may be a special label (4K, Stream, New etc)
                // Two cases to detect label: 1) Description is long (4 items); 2) First item of info is too short (2 letters)
                result = split.length < 4 && split[0].trim().length() > 2 ? split[0] : split[1];
            }
        }

        return result != null ? Helpers.abbreviate(result.trim(), MAX_AUTHOR_LENGTH_CHARS) : null;
    }

    public static List<Video> findVideosByAuthor(VideoGroup group, String author) {
        List<Video> result = new ArrayList<>();

        if (group != null && group.getVideos() != null) {
            for (Video video : group.getVideos()) {
                if (Helpers.equals(video.extractAuthor(), author)) {
                    result.add(video);
                }
            }
        }

        return result;
    }

    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(category);
        dest.writeString(title);
        dest.writeString(secondTitle);
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

        // 'playlistParams' backward compatibility
        if (split.length == 10) {
            split = Helpers.appendArray(split, new String[]{null});
        }

        // 'extra' backward compatibility
        if (split.length == 11) {
            split = Helpers.appendArray(split, new String[]{"-1"});
        }

        // 'reloadPageKey' backward compatibility
        if (split.length == 12) {
            split = Helpers.appendArray(split, new String[]{null});
        }

        // 'type' backward compatibility
        if (split.length == 13) {
            split = Helpers.appendArray(split, new String[]{"-1"});
        }

        if (split.length != 14) {
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
        result.playlistParams = Helpers.parseStr(split[10]);
        result.extra = Helpers.parseInt(split[11]);
        result.reloadPageKey = Helpers.parseStr(split[12]);
        result.itemType = Helpers.parseInt(split[13]);

        return result;
    }

    @Override
    public String toString() {
        return String.format("%s&vi;%s&vi;%s&vi;%s&vi;%s&vi;%s&vi;%s&vi;%s&vi;%s&vi;%s&vi;%s&vi;%s&vi;%s&vi;%s",
                id, category, title, videoId, videoUrl, playlistId, channelId, bgImageUrl, cardImageUrl,
                YouTubeMediaService.serialize(mediaItem), playlistParams, extra, getReloadPageKey(), itemType);
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

    public boolean hasVideo() {
        return videoId != null;
    }

    public boolean hasChannel() {
        return channelId != null;
    }

    /**
     * NOTE: Channels section uses <em>playlistParams</em> instead of <em>playlistId</em>
     */
    public boolean hasPlaylist() {
        return playlistId != null || (playlistParams != null && !Helpers.containsAny(playlistParams, sNotPlaylistParams));
    }

    /**
     * Persist on Channels and User playlists sections
     */
    public boolean hasReloadPageKey() {
        return getReloadPageKey() != null;
    }

    public boolean hasUploads() {
        return mediaItem != null && mediaItem.hasUploads();
    }

    public boolean isChannel() {
        return videoId == null && channelId != null;
    }

    public boolean hasPlaylistIndex() {
        return playlistIndex > 0;
    }

    /**
     * Special type of channels that work as playlist
     */
    public boolean isChannelPlaylist() {
        return videoId == null && channelId != null && itemType == MediaItem.TYPE_PLAYLIST;
    }

    public boolean belongsToPlaylists() {
        return group != null && group.getMediaGroup() != null && group.getMediaGroup().getType() == MediaGroup.TYPE_USER_PLAYLISTS;
    }

    public String getGroupTitle() {
        return group != null ? group.getTitle() : null;
    }

    /**
     * Persist on Channels and User playlists sections
     */
    public String getReloadPageKey() {
        return reloadPageKey != null ? reloadPageKey :
                (group != null && group.getMediaGroup() != null && group.getMediaGroup().getReloadPageKey() != null) ? group.getMediaGroup().getReloadPageKey() : null;
    }

    public boolean belongsToUndefined() {
        return group != null && group.getMediaGroup() != null && group.getMediaGroup().getType() == MediaGroup.TYPE_UNDEFINED;
    }

    public boolean belongsToSameAuthorGroup() {
        if (!checkMediaItems()) {
            return false;
        }

        List<MediaItem> mediaItems = group.getMediaGroup().getMediaItems();

        MediaItem first = mediaItems.get(0);
        MediaItem last = mediaItems.get(mediaItems.size() - 1);

        String author1 = extractAuthor(first.getSecondTitle());
        String author2 = extractAuthor(last.getSecondTitle());

        return author1 != null && author2 != null && Helpers.equals(author1, author2);
    }

    public boolean belongsToSamePlaylistGroup() {
        if (!checkMediaItems()) {
            return false;
        }

        List<MediaItem> mediaItems = group.getMediaGroup().getMediaItems();

        MediaItem first = mediaItems.get(0);
        MediaItem second = mediaItems.get(1);

        String playlist1 = first.getPlaylistId() != null ? first.getPlaylistId() : first.getPlaylistParams();
        String playlist2 = second.getPlaylistId() != null ? second.getPlaylistId() : second.getPlaylistParams();

        return playlist1 != null && playlist2 != null && Helpers.equals(playlist1, playlist2);
    }

    public boolean belongsToChannelUploads() {
        return group != null && group.getMediaGroup() != null && group.getMediaGroup().getType() == MediaGroup.TYPE_CHANNEL_UPLOADS;
    }

    public boolean belongsToSubscriptions() {
        return group != null && group.getMediaGroup() != null && group.getMediaGroup().getType() == MediaGroup.TYPE_SUBSCRIPTIONS;
    }

    public boolean belongsToHistory() {
        return group != null && group.getMediaGroup() != null && group.getMediaGroup().getType() == MediaGroup.TYPE_HISTORY;
    }

    public boolean belongsToMusic() {
        return group != null && group.getMediaGroup() != null && group.getMediaGroup().getType() == MediaGroup.TYPE_MUSIC;
    }

    public boolean belongsToSection() {
        return group != null && group.getSection() != null;
    }

    public void sync(Video video) {
        if (video == null) {
            return;
        }

        percentWatched = video.percentWatched;
    }

    public void sync(MediaItemMetadata metadata) {
        sync(metadata, false);
    }

    public void sync(MediaItemMetadata metadata, boolean useAltSecondTitle) {
        if (metadata == null) {
            return;
        }

        newTitle = metadata.getTitle();
        
        newSecondTitle = useAltSecondTitle ? metadata.getSecondTitleAlt() : metadata.getSecondTitle();

        // Casting fix (no title, no desc)
        if (title == null) {
            title = newTitle;
        }

        // Casting fix (no title, no desc)
        if (secondTitle == null) {
            secondTitle = newSecondTitle;
        }

        // No checks. This data wasn't existed before sync.
        if (metadata.getDescription() != null) {
            description = metadata.getDescription();
        }
        channelId = metadata.getChannelId();
        nextMediaItem = metadata.getNextVideo();
        // NOTE: Upcoming videos metadata wrongly reported as live
        isLive = metadata.isLive();
        isUpcoming = metadata.isUpcoming();
        isSubscribed = metadata.isSubscribed();
        isSynced = true;

        if (mediaItem != null) {
            mediaItem.sync(metadata);
        }
    }

    public void sync(MediaItemFormatInfo formatInfo) {
        if (formatInfo == null) {
            return;
        }

        if (description == null) {
            description = formatInfo.getDescription();
        }
    }

    /**
     * Creating lightweight copy of origin.
     */
    public Video copy() {
        Video video = from(videoId, playlistId, playlistIndex, channelId, title, secondTitle, percentWatched);
        if (group != null) {
            video.group = group.copy(); // Needed for proper multi row fragments sync (row id == group id)
        }
        return video;
    }

    public boolean canSubscribe() {
        return hasChannel() && !channelId.startsWith(SECTION_PREFIX);
    }

    public String getPlayerTitle() {
        return newTitle != null ? newTitle : title != null ? title : null;
    }

    public String getPlayerSecondTitle() {
        // Don't sync future translation because of not precise info
        return newSecondTitle != null && !isUpcoming ? newSecondTitle : secondTitle != null ? secondTitle : null;
    }

    private boolean checkMediaItems() {
        return group != null && group.getMediaGroup() != null
                && group.getMediaGroup().getMediaItems() != null && group.getMediaGroup().getMediaItems().size() >= 2;
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
