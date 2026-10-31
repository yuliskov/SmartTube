package com.liskovsoft.leanbackassistant.media;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.leanbackassistant.R;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.ContentService;

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
                R.string.header_subscriptions,
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
                R.string.header_history,
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
                R.string.recommended,
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
                callback,
                isDefault);
        playlist.setChannelKey(channelId);
        playlist.setProgramsKey(programId);
        playlist.setPlaylistUrl(recommendedUrl);
        playlist.setLogoResId(logoResId);

        return playlist;
    }

    public interface GroupCallback {
        MediaGroup call(ContentService mediaTabManager);
    }
}
