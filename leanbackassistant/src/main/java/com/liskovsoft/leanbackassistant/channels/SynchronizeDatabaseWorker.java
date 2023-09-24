package com.liskovsoft.leanbackassistant.channels;

import android.content.Context;
import android.os.Build.VERSION;
import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.liskovsoft.leanbackassistant.media.ClipService;
import com.liskovsoft.leanbackassistant.media.Playlist;
import com.liskovsoft.leanbackassistant.recommendations.RecommendationsProvider;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;

import java.util.concurrent.TimeUnit;

/**
 * Work to synchronize the TV provider database with the desired list of channels and
 * programs. This sample app runs this once at install time to publish an initial set of channels
 * and programs, however in a real-world setting this might be run at other times to synchronize
 * a server's database with the TV provider database.
 * This code will ensure that the channels from "SampleClipApi.getDesiredPublishedChannelSet()"
 * appear in the TV provider database, and that these and all other programs are synchronized with
 * TV provider database.
 */
public class SynchronizeDatabaseWorker extends Worker {
    private static final String TAG = SynchronizeDatabaseWorker.class.getSimpleName();
    private static final String WORK_NAME = "Update channels";
    private final Context mContext;
    private final GlobalPreferences mPrefs;
    private final ClipService mService;

    public SynchronizeDatabaseWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);

        mContext = context;

        Log.d(TAG, "Creating GlobalPreferences...");
        mPrefs = GlobalPreferences.instance(context);
        mService = ClipService.instance(context);
    }

    public static void schedule(Context context) {
        if (VERSION.SDK_INT >= 23 && GlobalPreferences.instance(context).isChannelsServiceEnabled()) {
            WorkManager workManager = WorkManager.getInstance(context);

            // https://stackoverflow.com/questions/50943056/avoiding-duplicating-periodicworkrequest-from-workmanager
            workManager.enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                    new PeriodicWorkRequest.Builder(SynchronizeDatabaseWorker.class, 20, TimeUnit.MINUTES).addTag(WORK_NAME).build()
            );
        }
    }

    public static void cancel(Context context) {
        if (VERSION.SDK_INT >= 23 && GlobalPreferences.instance(context).isChannelsServiceEnabled()) {
            Log.d(TAG, "Unregistering Channels update job...");

            WorkManager workManager = WorkManager.getInstance(context);
            workManager.cancelUniqueWork(WORK_NAME);
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        updateChannels();
        updateRecommendations();

        return Result.success();
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

                updateOrPublishRecommendations(playlist);
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void updateOrPublishRecommendations(Playlist playlist) {
        if (checkPlaylist(playlist)) {
            Log.d(TAG, "Syncing recommended: " + playlist.getName() + ", items num: " + playlist.getClips().size());
            RecommendationsProvider.createOrUpdateRecommendations(mContext, playlist);
        }
    }

    private void updateOrPublishChannel(Playlist playlist) {
        if (checkPlaylist(playlist)) {
            Log.d(TAG, "Syncing channel: " + playlist.getName() + ", items num: " + playlist.getClips().size());
            ChannelsProvider.createOrUpdateChannel(mContext, playlist);
        }
    }

    private boolean checkPlaylist(Playlist playlist) {
        return playlist != null && playlist.getClips() != null;
    }
}
