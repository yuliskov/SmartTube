package com.liskovsoft.leanbackassistant.channels;

import android.content.Context;

import androidx.annotation.RequiresApi;

import com.liskovsoft.leanbackassistant.media.ClipService;
import com.liskovsoft.leanbackassistant.media.Playlist;
import com.liskovsoft.leanbackassistant.recommendations.RecommendationsProvider;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;

@RequiresApi(21)
public class UpdateChannelsTask {
    private static final String TAG = UpdateChannelsTask.class.getSimpleName();
    private final Context mContext;
    private final GlobalPreferences mPrefs;
    private final ClipService mService;

    public UpdateChannelsTask(Context context) {
        mContext = context;

        Log.d(TAG, "Creating GlobalPreferences...");
        mPrefs = GlobalPreferences.instance(context);
        mService = ClipService.instance(context);
    }

    public void run() {
        updateChannels();
        updateRecommendations();
    }

    private void updateChannels() {
        if (Helpers.isATVChannelsSupported(mContext)) {
            try {
                updateOrPublishChannel(mService.getSubscriptionsPlaylist());
                updateOrPublishChannel(mService.getRecommendedPlaylist());
                updateOrPublishChannel(mService.getHistoryPlaylist());
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void updateRecommendations() {
        if (Helpers.isATVRecommendationsSupported(mContext)) {
            try {
                updateOrPublishRecommendations(getSinglePreferredPlaylist());
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private Playlist getSinglePreferredPlaylist() {
        Playlist playlist = null;

        switch (mPrefs.getRecommendedPlaylistType()) {
            case GlobalPreferences.PLAYLIST_TYPE_RECOMMENDATIONS:
                playlist = mService.getRecommendedPlaylist();
                break;
            case GlobalPreferences.PLAYLIST_TYPE_SUBSCRIPTIONS:
                playlist = mService.getSubscriptionsPlaylist();
                break;
            case GlobalPreferences.PLAYLIST_TYPE_HISTORY:
                playlist = mService.getHistoryPlaylist();
                break;
        }

        return playlist;
    }

    private void updateOrPublishRecommendations(Playlist playlist) {
        Log.d(TAG, "Syncing recommended: " + playlist.getName());
        RecommendationsProvider.createOrUpdateRecommendations(mContext, playlist);
    }

    private void updateOrPublishChannel(Playlist playlist) {
        Log.d(TAG, "Syncing channel: " + playlist.getName());
        ChannelsProvider.createOrUpdateChannel(mContext, playlist);
    }
}
