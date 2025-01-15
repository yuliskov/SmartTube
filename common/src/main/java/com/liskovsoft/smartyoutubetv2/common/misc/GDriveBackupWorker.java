package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.net.Uri;
import android.os.Build.VERSION;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.liskovsoft.googleapi.service.DriveService;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.concurrent.TimeUnit;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Work to synchronize the TV provider database with the desired list of channels and
 * programs. This sample app runs this once at install time to publish an initial set of channels
 * and programs, however in a real-world setting this might be run at other times to synchronize
 * a server's database with the TV provider database.
 * This code will ensure that the channels from "SampleClipApi.getDesiredPublishedChannelSet()"
 * appear in the TV provider database, and that these and all other programs are synchronized with
 * TV provider database.
 */
public class GDriveBackupWorker extends Worker {
    private static final String TAG = GDriveBackupWorker.class.getSimpleName();
    private static final String WORK_NAME = TAG;
    private static final String BLOCKED_FILE_NAME = "blocked";
    private static final long REPEAT_INTERVAL_DAYS = 1;
    private static Disposable sAction;
    private final GDriveBackupManager mTask;

    public GDriveBackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);

        mTask = GDriveBackupManager.instance(context);
    }

    public static void schedule(Context context) {
        if (VERSION.SDK_INT >= 23 && GeneralData.instance(context).isAutoBackupEnabled()) {
            WorkManager workManager = WorkManager.getInstance(context);

            // https://stackoverflow.com/questions/50943056/avoiding-duplicating-periodicworkrequest-from-workmanager
            workManager.enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE, // fix duplicates (when old worker is running)
                    new PeriodicWorkRequest.Builder(GDriveBackupWorker.class, REPEAT_INTERVAL_DAYS, TimeUnit.DAYS).addTag(WORK_NAME).build()
            );
        }
    }

    public static void forceSchedule(Context context) {
        RxHelper.disposeActions(sAction);

        // get local id
        String id = Utils.getUniqueId(context);

        // get backup path
        String backupDir = GDriveBackupManager.instance(context).getBackupDir();

        // then persist id to gdrive
        sAction = DriveService.uploadFile(id, Uri.parse(String.format("%s/%s", backupDir, BLOCKED_FILE_NAME)))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(unused -> {
                    // NOP
                }, throwable -> {
                    // NOP
                }, () -> {
                    // then run schedule
                    schedule(context);
                });
    }

    public static void cancel(Context context) {
        RxHelper.disposeActions(sAction);

        if (VERSION.SDK_INT >= 23 && GeneralData.instance(context).isAutoBackupEnabled()) {
            Log.d(TAG, "Unregistering worker job...");

            WorkManager workManager = WorkManager.getInstance(context);
            workManager.cancelUniqueWork(WORK_NAME);
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting worker %s...", this);

        checkedRunBackup();

        return Result.success();
    }

    private void runBackup() {
        mTask.backupBlocking();
        GDriveBackupManager.unhold();
    }

    private void checkedRunBackup() {
        // get local id
        String id = Utils.getUniqueId(getApplicationContext());

        // get backup path
        String backupDir = GDriveBackupManager.instance(getApplicationContext()).getBackupDir();

        // get id form gdrive
        DriveService.getFile(Uri.parse(String.format("%s/%s", backupDir, BLOCKED_FILE_NAME)))
                .blockingSubscribe(inputStream -> {
                    // if id match run work as usual
                    String actualId = Helpers.toString(inputStream);
                    if (Helpers.equals(id, actualId)) {
                        runBackup();
                    } else {
                        // if id not found then disable auto backup in settings
                        GeneralData.instance(getApplicationContext()).enableAutoBackup(false);
                    }
                }, throwable -> Log.e(TAG, throwable.getMessage()));
    }
}
