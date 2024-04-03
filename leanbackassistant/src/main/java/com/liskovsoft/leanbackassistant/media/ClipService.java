package com.liskovsoft.leanbackassistant.media;

import android.annotation.SuppressLint;
import android.content.Context;
import androidx.tvprovider.media.tv.TvContractCompat;
import com.liskovsoft.leanbackassistant.R;
import com.liskovsoft.mediaserviceinterfaces.yt.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.yt.MotherService;
import com.liskovsoft.mediaserviceinterfaces.yt.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.yt.ContentService;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.youtubeapi.service.YouTubeMotherService;

import java.util.ArrayList;
import java.util.List;

public class ClipService {
    private static final int SUBSCRIPTIONS_ID = 1;
    private static final int HISTORY_ID = 2;
    private static final int RECOMMENDED_ID = 3;
    private static final String SUBS_CHANNEL_ID = "subs_channel_id";
    private static final String SUBS_PROGRAMS_IDS = "subs_clips_ids";
    private static final String RECOMMENDED_CHANNEL_ID = "recommended_channel_id";
    private static final String RECOMMENDED_PROGRAMS_IDS = "recommended_programs_ids";
    private static final String HISTORY_CHANNEL_ID = "history_channel_id";
    private static final String HISTORY_PROGRAMS_IDS = "history_programs_ids";
    private static final String SUBSCRIPTIONS_URL = "https://www.youtube.com/tv#/zylon-surface?c=FEsubscriptions&resume";
    private static final String HISTORY_URL = "https://www.youtube.com/tv#/zylon-surface?c=FEmy_youtube&resume";
    private static final String RECOMMENDED_URL = "https://www.youtube.com/tv#/zylon-surface?c=default&resume";
    private static final int MIN_PLAYLIST_SIZE = 40;
    @SuppressLint("StaticFieldLeak")
    private static ClipService mInstance;
    private final Context mContext;

    public ClipService(Context context) {
        mContext = context;
    }

    public static ClipService instance(Context context) {
        if (mInstance == null) {
            mInstance = new ClipService(context.getApplicationContext());
        }

        return mInstance;
    }

    public Playlist getSubscriptionsPlaylist() {
        return createPlaylist(
                R.string.subscriptions_playlist_name,
                SUBSCRIPTIONS_ID,
                SUBS_CHANNEL_ID,
                SUBS_PROGRAMS_IDS,
                SUBSCRIPTIONS_URL,
                R.drawable.generic_channels,
                ContentService::getSubscriptions,
                false
        );
    }

    public Playlist getHistoryPlaylist() {
        return createPlaylist(
                R.string.history_playlist_name,
                HISTORY_ID,
                HISTORY_CHANNEL_ID,
                HISTORY_PROGRAMS_IDS,
                HISTORY_URL,
                R.drawable.generic_channels,
                ContentService::getHistory,
                false);
    }

    public Playlist getRecommendedPlaylist() {
        return createPlaylist(
                R.string.recommended_playlist_name,
                RECOMMENDED_ID,
                RECOMMENDED_CHANNEL_ID,
                RECOMMENDED_PROGRAMS_IDS,
                RECOMMENDED_URL,
                R.drawable.generic_channels,
                ContentService::getRecommended,
                true);
    }

    private Playlist createPlaylist(
            int titleResId, int id, String channelId, String programId,
            String recommendedUrl, int logoResId, GroupCallback callback, boolean isDefault) {
        Playlist playlist = new Playlist(
                mContext.getResources().getString(titleResId),
                Integer.toString(id),
                isDefault);
        playlist.setChannelKey(channelId);
        playlist.setProgramsKey(programId);
        playlist.setPlaylistUrl(recommendedUrl);
        playlist.setLogoResId(logoResId);

        MotherService service = YouTubeMotherService.instance();
        ContentService contentService = service.getContentService();
        MediaGroup selectedGroup = callback.call(contentService);

        if (selectedGroup != null) {
            List<MediaItem> mediaItems = selectedGroup.getMediaItems();
            List<Clip> clips;

            if (mediaItems != null && !mediaItems.isEmpty()) {
                for (int i = 0; i < 3; i++) {
                    if (mediaItems.size() >= MIN_PLAYLIST_SIZE) {
                        break;
                    }

                    MediaGroup mediaGroup = contentService.continueGroup(selectedGroup);
                    if (mediaGroup == null) {
                        break;
                    }
                    mediaItems.addAll(mediaGroup.getMediaItems());
                }

                // Fix duplicated items inside ATV channels???
                Helpers.removeDuplicates(mediaItems);

                clips = convertToClips(mediaItems);
            } else {
                clips = new ArrayList<>();
            }

            playlist.setClips(clips);
        }

        return playlist;
    }

    private List<Clip> convertToClips(List<MediaItem> videos) {
        if (videos != null) {
            List<Clip> clips = new ArrayList<>();

            for (MediaItem v : videos) {
                clips.add(new Clip(
                        v.getTitle(),
                        v.getSecondTitle(),
                        v.getDurationMs(),
                        v.getBackgroundImageUrl(),
                        v.getCardImageUrl(),
                        v.getVideoUrl(),
                        null,
                        false,
                        v.isLive(),
                        null,
                        Integer.toString(v.getId()),
                        null,
                        TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9));
            }

            return clips;
        }

        return null;
    }

    private interface GroupCallback {
        MediaGroup call(ContentService mediaTabManager);
    }
}
