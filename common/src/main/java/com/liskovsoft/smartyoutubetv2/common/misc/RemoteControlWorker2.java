package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.os.Build.VERSION;
import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.concurrent.TimeUnit;

/**
 * DD NOT WORKING<br/>
 * Work to check remote service state and restart if needed.
 */
public class RemoteControlWorker2 extends Worker {
    private static final String TAG = RemoteControlWorker2.class.getSimpleName();
    private static final String WORK_NAME = "Remote control check";
    private final Context mContext;

    public RemoteControlWorker2(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);

        mContext = context;
    }

    public static void schedule(Context context) {
        if (VERSION.SDK_INT > 19) { // Eltex NPE fix
            Log.d(TAG, "Registering Remote control check job...");

            // Start service again if needed
            // Service that prevents the app from destroying
            Utils.startService(context, RemoteControlService.class);

            WorkManager workManager = WorkManager.getInstance(context);

            // https://stackoverflow.com/questions/50943056/avoiding-duplicating-periodicworkrequest-from-workmanager
            workManager.enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    new PeriodicWorkRequest.Builder(RemoteControlWorker2.class, 20, TimeUnit.MINUTES).build()
            );
        }
    }

    public static void cancel(Context context) {
        if (VERSION.SDK_INT > 19) { // Eltex NPE fix
            Log.d(TAG, "Unregistering Remote control check job...");

            Utils.stopService(context, RemoteControlService.class);

            WorkManager workManager = WorkManager.getInstance(context);
            workManager.cancelUniqueWork(WORK_NAME);
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        // Start service again if needed
        // Service that prevents the app from destroying
        Utils.startService(mContext, RemoteControlService.class);

        return Result.success();
    }
}
