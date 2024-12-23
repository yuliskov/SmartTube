package com.liskovsoft.leanbackassistant.channels;

import android.content.Context;
import android.os.Build.VERSION;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

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
public class UpdateChannelsWorker extends Worker {
    private static final String TAG = UpdateChannelsWorker.class.getSimpleName();
    private static final String WORK_NAME = "Update channels";
    private static final long REPEAT_INTERVAL_MINUTES = 15; // 15 - minimal interval
    private final UpdateChannelsTask mTask;

    public UpdateChannelsWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);

        mTask = new UpdateChannelsTask(context);
    }

    public static void schedule(Context context) {
        if (VERSION.SDK_INT >= 23 && GlobalPreferences.instance(context).isChannelsServiceEnabled()) {
            WorkManager workManager = WorkManager.getInstance(context);

            // https://stackoverflow.com/questions/50943056/avoiding-duplicating-periodicworkrequest-from-workmanager
            workManager.enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE, // fix duplicates (when old worker is running)
                    new PeriodicWorkRequest.Builder(UpdateChannelsWorker.class, REPEAT_INTERVAL_MINUTES, TimeUnit.MINUTES).addTag(WORK_NAME).build()
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
        Log.d(TAG, "Starting worker %s...", this);

        // Improve performance. Run task when the app paused.
        if (!Helpers.isAppInForeground()) {
            mTask.run();
        }

        return Result.success();
    }
}
