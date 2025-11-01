package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liskovsoft.mediaserviceinterfaces.data.ItemGroup;
import com.liskovsoft.mediaserviceinterfaces.data.ChapterItem;
import com.liskovsoft.mediaserviceinterfaces.data.DislikeData;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.ItemGroup.Item;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.mediaserviceinterfaces.data.NotificationState;
import com.liskovsoft.mediaserviceinterfaces.data.PlaylistInfo;
import com.liskovsoft.sharedutils.helpers.DateHelper;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.googlecommon.common.helpers.ServiceHelper;
import com.liskovsoft.googlecommon.common.helpers.YouTubeHelper;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Video is an object that holds the various metadata associated with a single video.
 */
public final class Video {
    public static final String PLAYLIST_LIKED_MUSIC = "LM";
    public static final String TERTIARY_TEXT_DELIM = "•";
    public static final long MAX_LIVE_DURATION_MS = 24 * 60 * 60 * 1_000;
    private static final int MAX_AUTHOR_LENGTH_CHARS = 20;
    private static final String BLACK_PLACEHOLDER_URL = "https://via.placeholder.com/1280x720/000000/000000";
    private static final float RESTORE_POSITION_PERCENTS = 10; // min value for immediately closed videos
    public int id;
    public String title;
    public String deArrowTitle;
    public CharSequence secondTitle;
    private String metadataTitle;
    private CharSequence metadataSecondTitle;
    public String description;
    public String category;
    public int itemType = -1;
    public String channelId;
    public String videoId;
    public String playlistId;
    public String remotePlaylistId;
    public int playlistIndex = -1;
    public String playlistParams;
    public String reloadPageKey;
    public String bgImageUrl;
    public String cardImageUrl;
    public String altCardImageUrl;
    public String author;
    public String badge;
    public String previewUrl;
    public float percentWatched = -1;
    public int startTimeSeconds;
    public MediaItem mediaItem;
    public MediaItem nextMediaItem;
    public MediaItem shuffleMediaItem;
    public PlaylistInfo playlistInfo;
    public boolean hasNewContent;
    public boolean isLive;
    public boolean isUpcoming;
    public boolean isUnplayable;
    public boolean isShorts;
    public boolean isChapter;
    public boolean isMovie;
    public boolean isSubscribed;
    public boolean isRemote;
    public int groupPosition = -1; // group position in multi-grid fragments
    public String clickTrackingParams;
    public boolean isSynced;
    public final long timestamp = System.currentTimeMillis();
    public int sectionId = -1;
    public String channelGroupId;
    public long startTimeMs;
    public long pendingPosMs;
    public boolean fromQueue;
    public boolean isPending;
    public boolean finishOnEnded;
    public boolean incognito;
    public String likeCount;
    public String dislikeCount;
    public String subscriberCount;
    public float volume = 1.0f;
    public boolean deArrowProcessed;
    public boolean isLiveEnd;
    public boolean isShuffled;
    public String searchQuery;
    private int startSegmentNum;
    private long liveDurationMs = -1;
    private long durationMs = -1;
    private WeakReference<VideoGroup> group; // Memory leak fix. Used to get next page when scrolling.
    public List<NotificationState> notificationStates;

    public Video() {
       // NOP
    }

    private Video(
            final int id,
            final String category,
            final String title,
            final String desc,
            final String videoId,
            final String bgImageUrl,
            final String cardImageUrl,
            final String author) {
        this.id = id;
        this.category = category;
        this.title = title;
        this.secondTitle = desc;
        this.videoId = videoId;
        this.bgImageUrl = bgImageUrl;
        this.cardImageUrl = cardImageUrl;
        this.author = author;
    }

    public static Video from(MediaItem item) {
        if (item == null) {
            return null;
        }

        Video video = new Video();

        video.id = item.getId();
        video.title = item.getTitle();
        video.secondTitle = item.getSecondTitle();
        video.category = item.getContentType();
        video.itemType = item.getType();
        video.videoId = item.getVideoId();
        video.channelId = item.getChannelId();
        video.bgImageUrl = item.getBackgroundImageUrl();
        video.cardImageUrl = item.getCardImageUrl();
        video.author = item.getAuthor();
        video.percentWatched = item.getPercentWatched();
        video.startTimeSeconds = item.getStartTimeSeconds();
        video.badge = item.getBadgeText();
        video.hasNewContent = item.hasNewContent();
        video.previewUrl = item.getVideoPreviewUrl();
        video.playlistId = item.getPlaylistId();
        video.playlistIndex = item.getPlaylistIndex();
        video.playlistParams = item.getParams();
        video.reloadPageKey = item.getReloadPageKey();
        video.isLive = item.isLive();
        video.isUpcoming = item.isUpcoming();
        video.isShorts = item.isShorts();
        video.isMovie = item.isMovie();
        video.clickTrackingParams = item.getClickTrackingParams();
        video.durationMs = item.getDurationMs();
        video.searchQuery = item.getSearchQuery();
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
        video.reloadPageKey = item.getReloadPageKey();
        video.isLive = item.isLive;
        video.isUpcoming = item.isUpcoming;
        video.clickTrackingParams = item.clickTrackingParams;
        video.mediaItem = item.mediaItem;
        video.group = item.group;

        return video;
    }

    public static Video from(String videoId) {
        Video video = new Video();
        video.videoId = videoId;
        return video;
    }

    public static Video from(ChapterItem chapter) {
        Video video = new Video();
        video.isChapter = true;
        video.title = chapter.getTitle();
        video.cardImageUrl = chapter.getCardImageUrl();
        video.startTimeMs = chapter.getStartTimeMs();
        video.badge = ServiceHelper.millisToTimeText(chapter.getStartTimeMs());
        return video;
    }

    public static Video from(ItemGroup group) {
        Video video = new Video();
        video.title = group.getTitle();
        video.cardImageUrl = group.getIconUrl();
        video.channelGroupId = group.getId();
        return video;
    }

    public static Video from(Item channel) {
        Video video = new Video();
        video.title = channel.getTitle();
        video.cardImageUrl = channel.getIconUrl();
        video.channelId = channel.getChannelId();
        return video;
    }
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

            return hashCode() == video.hashCode() && isMix() == video.isMix();
        }

        return false;
    }

    /**
     * NOTE: hashCode is used generate id that should be the same if contents of items is the same
     */
    @Override
    public int hashCode() {
        // NOTE: With full hash code won't jump to last known position
        int hashCode = Helpers.hashCodeAny(videoId, playlistId, reloadPageKey, playlistParams, channelId, sectionId, channelGroupId, mediaItem);
        return hashCode != -1 ? hashCode : super.hashCode();
    }

    public static void printDebugInfo(Context context, Video item) {
        MessageHelpers.showLongMessage(context,
                String.format("videoId=%s, playlistId=%s, reloadPageKey=%s, playlistParams=%s, channelId=%s, mediaItem=%s, extra=%s",
                        item.videoId, item.playlistId, item.reloadPageKey, item.playlistParams, item.channelId, item.mediaItem, item.sectionId)
        );
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

    public int getId() {
        if (id == 0 || id == -1) {
            id = hashCode();
        }

        return id;
    }

    public String getTitle() {
        return deArrowTitle != null ? deArrowTitle : title;
    }

    public String getTitleFull() {
        return deArrowTitle != null ? deArrowTitle : metadataTitle != null ? metadataTitle : title;
    }

    public CharSequence getSecondTitle() {
        return secondTitle;
    }

    public CharSequence getSecondTitleFull() {
        // Don't sync future translation because of not precise info
        return metadataSecondTitle != null && !isUpcoming ? metadataSecondTitle : secondTitle;
    }

    public String getPlaylistId() {
        return isRemote && remotePlaylistId != null ? remotePlaylistId : playlistId;
    }

    public String getCardImageUrl() {
        return altCardImageUrl != null ? altCardImageUrl : cardImageUrl;
    }

    public String getAuthor() {
        if (author != null) {
            return author;
        }

        String mainTitle = metadataTitle != null ? metadataTitle : title;
        CharSequence subtitle = metadataSecondTitle != null ? metadataSecondTitle : secondTitle;
        return hasVideo() ? extractAuthor(subtitle) : Helpers.toString(YouTubeHelper.createInfo(mainTitle, subtitle)); // BAD idea
    }

    public VideoGroup getGroup() {
        return group != null ? group.get() : null;
    }

    public void setGroup(VideoGroup group) {
        this.group = new WeakReference<>(group);
    }

    public int getPositionInsideGroup() {
        return getGroup() != null && !getGroup().isEmpty() ? getGroup().getVideos().indexOf(this) : -1;
    }

    private static String extractAuthor(CharSequence secondTitle) {
        return extractAuthor(Helpers.toString(secondTitle));
    }

    private static String extractAuthor(String secondTitle) {
        String result = null;

        if (secondTitle != null) {
            secondTitle = secondTitle.replace(TERTIARY_TEXT_DELIM + " LIVE", ""); // remove special marks
            String[] split = secondTitle.split(TERTIARY_TEXT_DELIM);

            if (split.length <= 1) {
                result = secondTitle;
            } else {
                // First part may be a special label (4K, Stream, New etc)
                // Two cases to detect label: 1) Description is long (4 items); 2) First item of info is too short (2 letters)
                result = split.length < 4 && split[0].trim().length() > 2 ? split[0] : split[1];
            }
        }

        // Skip subtitles starting with number of views (e.g. 1.4M views)
        return !TextUtils.isEmpty(result) && !Helpers.isNumeric(result.substring(0, 1)) ? Helpers.abbreviate(result.trim(), MAX_AUTHOR_LENGTH_CHARS) : null;
    }

    public static List<Video> findVideosByAuthor(VideoGroup group, String author) {
        List<Video> result = new ArrayList<>();

        if (group != null && group.getVideos() != null) {
            for (Video video : group.getVideos()) {
                if (Helpers.equals(video.getAuthor(), author)) {
                    result.add(video);
                }
            }
        }

        return result;
    }

    public int describeContents() {
        return 0;
    }

    public static Video fromString(String spec) {
        if (spec == null) {
            return null;
        }

        String[] split = Helpers.splitObj(spec);

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

        if (split.length == 14) {
            split = Helpers.appendArray(split, new String[]{null});
        }

        if (split.length == 15) {
            split = Helpers.appendArray(split, new String[]{null});
        }

        if (split.length == 16) {
            split = Helpers.appendArray(split, new String[]{"-1"});
        }

        if (split.length == 17) {
            split = Helpers.appendArray(split, new String[]{null});
        }

        if (split.length == 18) {
            split = Helpers.appendArray(split, new String[]{null});
        }

        if (split.length == 19) {
            split = Helpers.appendArray(split, new String[]{null});
        }

        if (split.length == 20) {
            split = Helpers.appendArray(split, new String[]{"false"});
        }

        if (split.length == 21) {
            split = Helpers.appendArray(split, new String[]{"-1"});
        }

        if (split.length == 22) {
            split = Helpers.appendArray(split, new String[]{null});
        }

        if (split.length != 23) {
            return null;
        }

        Video result = new Video();

        result.id = Helpers.parseInt(split[0]);
        result.category = Helpers.parseStr(split[1]);
        result.title = Helpers.parseStr(split[2]);
        result.videoId = Helpers.parseStr(split[3]);
        //result.videoUrl = Helpers.parseStr(split[4]);
        result.playlistId = Helpers.parseStr(split[5]);
        result.channelId = Helpers.parseStr(split[6]);
        result.bgImageUrl = Helpers.parseStr(split[7]);
        result.cardImageUrl = Helpers.parseStr(split[8]);
        //result.mediaItem = YouTubeMediaItem.deserializeMediaItem(Helpers.parseStr(split[9]));
        result.playlistParams = Helpers.parseStr(split[10]);
        result.sectionId = Helpers.parseInt(split[11]);
        result.reloadPageKey = Helpers.parseStr(split[12]);
        result.itemType = Helpers.parseInt(split[13]);
        result.secondTitle = Helpers.parseStr(split[14]);
        result.previewUrl = Helpers.parseStr(split[15]);
        result.percentWatched = Helpers.parseFloat(split[16]);
        result.metadataTitle = Helpers.parseStr(split[17]);
        result.metadataSecondTitle = Helpers.parseStr(split[18]);
        result.badge = Helpers.parseStr(split[19]);
        result.isLive = Helpers.parseBoolean(split[20]);
        result.channelGroupId = Helpers.parseStr(split[21]);
        result.searchQuery = Helpers.parseStr(split[22]);

        // Reset old type (int)
        if (Helpers.equals(result.channelGroupId, "-1")) {
            result.channelGroupId = null;
        }

        return result;
    }

    @NonNull
    @Override
    public String toString() {
        return Helpers.mergeObj(id, category, title, videoId, null, playlistId, channelId, bgImageUrl, cardImageUrl,
                null, playlistParams, sectionId, getReloadPageKey(), itemType, secondTitle, previewUrl, percentWatched,
                metadataTitle, metadataSecondTitle, badge, isLive, channelGroupId, searchQuery);
    }

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
        return playlistId != null;
    }

    //public boolean hasPlaylist() {
    //    return playlistId != null || (playlistParams != null && !Helpers.containsAny(playlistParams, sNotPlaylistParams));
    //}

    public boolean hasNextPlaylist() {
        return hasNextItem() && getPlaylistId() != null && getPlaylistId().equals(nextMediaItem.getPlaylistId());
    }

    /**
     * Persist on Channels and User playlists sections
     */
    public boolean hasReloadPageKey() {
        return getReloadPageKey() != null;
    }

    public boolean hasNextPageKey() {
        return getNextPageKey() != null;
    }

    public boolean hasNextItem() {
        return nextMediaItem != null;
    }

    public boolean hasNestedItems() {
        return mediaItem != null && mediaItem.hasUploads();
    }

    public boolean hasPlaylistIndex() {
        return playlistIndex > 0;
    }

    public boolean isChannel() {
        return videoId == null && playlistId == null && channelId != null;
    }

    /**
     * Special type of channels that work as playlist
     */
    public boolean isPlaylistAsChannel() {
        return videoId == null && channelId != null && itemType == MediaItem.TYPE_PLAYLIST;
    }

    public boolean isPlaylistInChannel() {
        return belongsToChannel() && hasPlaylist() && !belongsToSamePlaylistGroup();
    }

    public boolean isMix() {
        return mediaItem != null && !isLive && !isLiveEnd && Helpers.hasWords(badge) &&
                (durationMs <= 0 || isSynced) && (hasPlaylist() || hasChannel() || hasNestedItems());
    }

    public boolean isFullLive() {
        return isLive && startSegmentNum == 0;
    }

    public boolean isEmpty() {
        if (isChapter) {
            return false;
        }

        // NOTE: Movies labeled as "Free with Ads" not supported yet
        return Helpers.allNulls(videoId, playlistId, reloadPageKey, playlistParams, channelId, searchQuery) || isMovie;
    }

    public String getGroupTitle() {
        return getGroup() != null ? getGroup().getTitle() : null;
    }

    /**
     * Persist on Channels and User playlists sections
     */
    public String getReloadPageKey() {
        return reloadPageKey != null ? reloadPageKey :
                getGroup() != null ? getGroup().getReloadPageKey() : null;
    }

    public String getNextPageKey() {
        return getGroup() != null ? getGroup().getNextPageKey() : null;
    }

    public String getBackgroundUrl() {
        return bgImageUrl != null ? bgImageUrl : BLACK_PLACEHOLDER_URL;
    }

    public boolean belongsToSameAuthorGroup() {
        if (getGroup() == null || getGroup().getSize() < 2) {
            return false;
        }

        Video first = getGroup().get(0);
        Video last = getGroup().get(getGroup().getSize() - 1);

        String author1 = extractAuthor(first.getSecondTitle());
        String author2 = extractAuthor(last.getSecondTitle());

        return author1 != null && author2 != null && Helpers.equals(author1, author2);
    }

    public boolean belongsToSamePlaylistGroup() {
        if (getGroup() == null || getGroup().getSize() < 2) {
            return false;
        }

        // Some items may not have a playlistId (e.g. movies)
        List<Video> filtered = Helpers.filter(getGroup().getVideos(), item -> item.getPlaylistId() != null || item.playlistParams != null, 2);

        if (filtered == null || filtered.size() < 2) {
            return false;
        }

        Video first = filtered.get(0);
        Video second = filtered.get(1);

        String playlist1 = first.getPlaylistId() != null ? first.getPlaylistId() : first.playlistParams;
        String playlist2 = second.getPlaylistId() != null ? second.getPlaylistId() : second.playlistParams;

        return playlist1 != null && playlist2 != null && Helpers.equals(playlist1, playlist2);
    }

    private boolean checkAllVideosHasPlaylist() {
        if (getGroup() == null || getGroup().getSize() < 2) {
            return false;
        }

        return playlistId != null && getGroup().get(0).playlistId != null && getGroup().get(1).playlistId != null && getGroup().get(getGroup().getSize() - 1).playlistId != null;
    }

    public boolean belongsToHome() {
        return belongsToGroup(MediaGroup.TYPE_HOME);
    }

    public boolean belongsToChannel() {
        return belongsToGroup(MediaGroup.TYPE_CHANNEL);
    }

    public boolean belongsToChannelUploads() {
        return belongsToGroup(MediaGroup.TYPE_CHANNEL_UPLOADS);
    }

    public boolean belongsToSubscriptions() {
        return belongsToGroup(MediaGroup.TYPE_SUBSCRIPTIONS);
    }

    public boolean belongsToHistory() {
        return belongsToGroup(MediaGroup.TYPE_HISTORY);
    }

    public boolean belongsToMusic() {
        return belongsToGroup(MediaGroup.TYPE_MUSIC);
    }

    public boolean belongsToShorts() {
        return belongsToGroup(MediaGroup.TYPE_SHORTS);
    }

    public boolean belongsToShortsGroup() {
        return isShorts && (belongsToShorts() || belongsToHome());
    }

    public boolean belongsToSearch() {
        return belongsToGroup(MediaGroup.TYPE_SEARCH);
    }

    public boolean belongsToNotifications() {
        return belongsToGroup(MediaGroup.TYPE_NOTIFICATIONS);
    }

    public boolean belongsToPlaybackQueue() {
        return belongsToGroup(MediaGroup.TYPE_PLAYBACK_QUEUE);
    }

    public boolean belongsToSuggestions() {
        return belongsToGroup(MediaGroup.TYPE_SUGGESTIONS);
    }

    public boolean belongsToUserPlaylists() {
        return belongsToGroup(MediaGroup.TYPE_USER_PLAYLISTS);
    }

    public boolean belongsToUndefined() {
        return belongsToGroup(MediaGroup.TYPE_UNDEFINED);
    }

    private boolean belongsToGroup(int groupId) {
        return getGroup() != null && getGroup().getType() == groupId;
    }

    public boolean belongsToSection() {
        return getGroup() != null && getGroup().getSection() != null;
    }

    public void sync(Video video) {
        if (video == null) {
            return;
        }

        percentWatched = video.percentWatched;
    }

    public void sync(MediaItemMetadata metadata) {
        if (metadata == null) {
            return;
        }

        if (isLive && !metadata.isLive()) {
            isLiveEnd = true;
        }

        // NOTE: Skip upcoming (no media) because default title more informative (e.g. has scheduled time).
        // NOTE: Upcoming videos metadata wrongly reported as live
        metadataTitle = metadata.getTitle();
        metadataSecondTitle = metadata.getSecondTitle();
        // NOTE: Upcoming videos metadata wrongly reported as live (live == true, upcoming == false)
        isLive = metadata.isLive();
        isUpcoming = metadata.isUpcoming();

        // No checks. This data wasn't existed before sync.
        if (metadata.getDescription() != null) {
            description = metadata.getDescription();
        }
        channelId = metadata.getChannelId();
        nextMediaItem = findNextVideo(metadata);
        shuffleMediaItem = metadata.getShuffleVideo();
        playlistInfo = metadata.getPlaylistInfo();
        isSubscribed = metadata.isSubscribed();
        likeCount = metadata.getLikeCount();
        dislikeCount = metadata.getDislikeCount();
        subscriberCount = metadata.getSubscriberCount();
        notificationStates = metadata.getNotificationStates();
        author = metadata.getAuthor();
        durationMs = metadata.getDurationMs();
        isSynced = true;
    }

    public void sync(MediaItemFormatInfo formatInfo) {
        if (formatInfo == null) {
            return;
        }
        
        isLive = formatInfo.isLive();

        if (description == null) {
            description = formatInfo.getDescription();
        }

        // Published time used on live videos only
        if (formatInfo.isLive()) {
            startTimeMs = formatInfo.getStartTimeMs() > 0 ? formatInfo.getStartTimeMs() : DateHelper.toUnixTimeMs(formatInfo.getStartTimestamp());
            startSegmentNum = formatInfo.getStartSegmentNum();
        }

        volume = formatInfo.getVolumeLevel();
        isUnplayable = formatInfo.isUnplayable();
    }

    public void sync(DislikeData dislikeData) {
        if (dislikeData == null) {
            return;
        }

        String likeCountNew = dislikeData.getLikeCount();
        String dislikeCountNew = dislikeData.getDislikeCount();
        if (likeCountNew != null) {
            likeCount = likeCountNew;
        }
        if (dislikeCountNew != null) {
            dislikeCount = dislikeCountNew;
        }
    }

    /**
     * Creating lightweight copy of origin.
     */
    public Video copy() {
        Video video = new Video();
        video.videoId = videoId;
        video.playlistId = playlistId;
        video.playlistIndex = playlistIndex;
        video.channelId = channelId;
        video.title = title;
        video.metadataTitle = metadataTitle;
        video.secondTitle = secondTitle;
        video.metadataSecondTitle = metadataSecondTitle;
        video.percentWatched = percentWatched;
        video.cardImageUrl = cardImageUrl;
        video.fromQueue = fromQueue;
        video.bgImageUrl = bgImageUrl;
        video.isLive = isLive;
        video.isUpcoming = isUpcoming;
        video.nextMediaItem = nextMediaItem;
        video.shuffleMediaItem = shuffleMediaItem;
        video.durationMs = durationMs;

        if (getGroup() != null) {
            video.setGroup(getGroup().copy()); // Needed for proper multi row fragments sync (row id == group id)
        }

        return video;
    }

    private MediaItem findNextVideo(MediaItemMetadata metadata) {
        if (metadata == null) {
            return null;
        }

        MediaItem nextVideo = metadata.getNextVideo();

        // BUGFIX: player closed after last video from the remote queue
        if (nextVideo == null && isRemote) {
            List<MediaGroup> suggestions = metadata.getSuggestions();

            if (suggestions != null && suggestions.size() > 1) {
                List<MediaItem> mediaItems = suggestions.get(1).getMediaItems();
                nextVideo = Helpers.findFirst(mediaItems, item -> item.getVideoId() != null);
            }
        }

        return nextVideo;
    }

    public void markFullyViewed() {
        percentWatched = 100;
        startTimeSeconds = (int)(getDurationMs() / 1_000);
    }

    public void markNotViewed() {
        percentWatched = 0;
        startTimeSeconds = 0;
    }

    public long getLiveDurationMs() {
        if (startTimeMs == 0) {
            return 0;
        }

        // Disable updates if stream ended while watching
        if (!isLive) {
            // Stream ended. We can use real duration now.
            return durationMs > 0 ? durationMs : liveDurationMs;
        }

        // Is stream real length may exceeds calculated length???
        liveDurationMs = System.currentTimeMillis() - startTimeMs;
        return liveDurationMs > 0 ? liveDurationMs : 0;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public long getPositionMs() {
        if (startTimeSeconds > 0) {
            return startTimeSeconds * 1_000L;
        }

        return getPositionFromPercentWatched();
    }

    private long getPositionFromPercentWatched() {
        // Ignore up to 10% watched because the video might be opened on phone and closed immediately.
        if (percentWatched <= RESTORE_POSITION_PERCENTS || percentWatched >= 100) {
            return 0;
        }

        long posMs = (long) (durationMs / 100f * percentWatched);
        return posMs > 0 && posMs < durationMs ? posMs : 0;
    }

    public MediaItem toMediaItem() {
        return SimpleMediaItem.from(this);
    }

    public void sync(VideoStateService.State state) {
        if (state != null) {
            percentWatched = state.positionMs / (state.durationMs / 100f);
        }
    }

    ///**
    // * The section playlist intended (as a backup replacement) for cases when regular playlist not available
    // */
    //public boolean isSectionPlaylistEnabled(Context context) {
    //    return PlayerTweaksData.instance(context).isSectionPlaylistEnabled() && !belongsToSuggestions()
    //            && (!checkAllVideosHasPlaylist() || PLAYLIST_LIKED_MUSIC.equals(playlistId) || nextMediaItem == null
    //                   || (!isMix() && !belongsToSamePlaylistGroup())) // skip hidden playlists (music videos usually)
    //            && (!isRemote || remotePlaylistId == null);
    //}

    /**
     * The section playlist intended (as a backup replacement) for cases when regular playlist not available
     */
    public boolean isSectionPlaylistEnabled(Context context) {
        return PlayerTweaksData.instance(context).isSectionPlaylistEnabled() && !belongsToSuggestions() && !belongsToPlaybackQueue()
                && (!checkAllVideosHasPlaylist() || nextMediaItem == null || !isMix()) // skip hidden playlists (music videos usually)
                && (!isRemote || remotePlaylistId == null);
    }

    public String createPlaylistTitle() {
        if (!hasPlaylist()) {
            return null;
        }
        
        // Trying to properly format channel playlists, mixes etc
        boolean isChannelPlaylistItem = getGroupTitle() != null && belongsToSameAuthorGroup() && belongsToSamePlaylistGroup();
        boolean isUserPlaylistItem = getGroupTitle() != null && belongsToSamePlaylistGroup();
        String title = isChannelPlaylistItem ? getAuthor() : isUserPlaylistItem ? null : getTitle();
        String subtitle = isChannelPlaylistItem || isUserPlaylistItem || belongsToUserPlaylists() ? getGroupTitle() : getAuthor();
        return title != null && subtitle != null ? String.format("%s - %s", title, subtitle) : String.format("%s", title != null ? title : subtitle);
    }

    public String createChannelTitle() {
        if (!hasReloadPageKey() && !hasChannel()) {
            return null;
        }
        
        // Trying to properly format channel playlists, mixes etc
        boolean hasChannel = hasChannel() && !isChannel();
        boolean isUserPlaylistItem = getGroupTitle() != null && belongsToSamePlaylistGroup();
        String title = hasChannel ? getAuthor() : isUserPlaylistItem ? null : getTitle();
        String subtitle = isUserPlaylistItem ? getGroupTitle() : hasChannel || isChannel() ? null : getAuthor();
        return title != null && subtitle != null ? String.format("%s - %s", title, subtitle) : String.format("%s", title != null ? title : subtitle);
    }
}
