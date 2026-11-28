package com.liskovsoft.leanbackassistant.channels;

import android.content.Context;
import android.os.Build.VERSION;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import com.liskovsoft.leanbackassistant.utils.SafeWorkManager;
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
@RequiresApi(21)
public class UpdateChannelsWorker extends Worker {
    private static final String TAG = UpdateChannelsWorker.class.getSimpleName();
    public static final String WORK_NAME = "Update channels";
    public static final long REPEAT_INTERVAL_MINUTES = 60;
    private final UpdateChannelsTask mTask;
    private static volatile boolean sIsWorkScheduled;

    public UpdateChannelsWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);

        mTask = new UpdateChannelsTask(context);
    }

    public static void schedule(Context context) {
        if (VERSION.SDK_INT < 23) {
            return;
        }

        if (!GlobalPreferences.instance(context).isChannelsServiceEnabled()) {
            cancel(context);
            return;
        }

        sIsWorkScheduled = true;

        SafeWorkManager workManager = SafeWorkManager.getInstance(context);

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        // https://stackoverflow.com/questions/50943056/avoiding-duplicating-periodicworkrequest-from-workmanager
        workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                //ExistingPeriodicWorkPolicy.UPDATE, // fix duplicates (when old worker is running)
                //ExistingPeriodicWorkPolicy.KEEP, // only enqueue if no work with this name exists
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, // called immediately!
                new PeriodicWorkRequest.Builder(UpdateChannelsWorker.class, REPEAT_INTERVAL_MINUTES, TimeUnit.MINUTES)
                        .setConstraints(constraints)
                        .addTag(WORK_NAME)
                        .build()
        );
    }

    public static void cancel(Context context) {
        if (VERSION.SDK_INT >= 23) {
            Log.d(TAG, "Unregistering Channels update job...");

            SafeWorkManager workManager = SafeWorkManager.getInstance(context);
            workManager.cancelUniqueWork(WORK_NAME);
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting worker %s...", this);

        if (!GlobalPreferences.instance(getApplicationContext()).isChannelsServiceEnabled()) {
            cancel(getApplicationContext());
            return Result.success();
        }

        if (!isDeviceInteractive(getApplicationContext())) {
            Log.d(TAG, "Device is not interactive; skipping channels sync.");
            return Result.success();
        }

        // Foreground ⇒ possible playback; skip sync to avoid CPU contention during video decode (#6234)
        if (!Helpers.isAppInForeground() || sIsWorkScheduled) {
            sIsWorkScheduled = false;
            mTask.run();
        }

        return Result.success();
    }

    public static boolean isDeviceInteractive(Context context) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return powerManager == null || powerManager.isInteractive();
    }
}
