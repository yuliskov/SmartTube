package com.liskovsoft.leanbackassistant.channels;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build.VERSION;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;

/**
 * JobScheduler task to synchronize the TV provider database with the desired list of channels and
 * programs. This sample app runs this once at install time to publish an initial set of channels
 * and programs, however in a real-world setting this might be run at other times to synchronize
 * a server's database with the TV provider database.
 * This code will ensure that the channels from "SampleClipApi.getDesiredPublishedChannelSet()"
 * appear in the TV provider database, and that these and all other programs are synchronized with
 * TV provider database.
 */
@TargetApi(21)
public class UpdateChannelsJobService extends JobService {
    private static final String TAG = UpdateChannelsJobService.class.getSimpleName();
    private static final int SYNC_JOB_ID = 1;
    private static boolean sInProgress;
    private SynchronizeDatabaseTask mSynchronizeDatabaseTask;

    public static void schedule(Context context) {
        if (VERSION.SDK_INT >= 23 && GlobalPreferences.instance(context).isChannelsServiceEnabled()) {
            Log.d(TAG, "Registering Channels update job...");
            JobScheduler scheduler = context.getSystemService(JobScheduler.class);

            // setup scheduled job
            scheduler.schedule(
                    new JobInfo.Builder(SYNC_JOB_ID, new ComponentName(context, UpdateChannelsJobService.class))
                            //.setPeriodic(TimeUnit.MINUTES.toMillis(30))
                            .setPeriodic(20 * 60 * 1_000)
                            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                            .setRequiresDeviceIdle(false)
                            .setRequiresCharging(false)
                            .build());
        }
    }

    public static void cancel(Context context) {
        if (VERSION.SDK_INT >= 23 && GlobalPreferences.instance(context).isChannelsServiceEnabled()) {
            Log.d(TAG, "Registering Channels update job...");
            JobScheduler scheduler = context.getSystemService(JobScheduler.class);

            scheduler.cancel(SYNC_JOB_ID);
        }
    }

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        if (sInProgress) {
            Log.d(TAG, "Channels update job already in progress. Exiting...");
            return true;
        }

        Log.d(TAG, "Starting Channels update job...");

        sInProgress = true;

        mSynchronizeDatabaseTask = new SynchronizeDatabaseTask(this, jobParameters);
        // NOTE: fetching channels in background
        mSynchronizeDatabaseTask.execute();

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        if (mSynchronizeDatabaseTask != null) {
            mSynchronizeDatabaseTask.cancel(true);
            mSynchronizeDatabaseTask = null;
        }

        sInProgress = false;

        return true;
    }

    /**
     * Publish any default channels not already published.
     */
    private class SynchronizeDatabaseTask extends AsyncTask<Void, Void, Exception> {
        private final JobParameters mJobParameters;
        private final UpdateChannelsTask mTask;

        SynchronizeDatabaseTask(Context context, JobParameters jobParameters) {
            mJobParameters = jobParameters;

            mTask = new UpdateChannelsTask(context);
        }

        @Override
        protected Exception doInBackground(Void... params) {
            mTask.run();

            return null;
        }

        @Override
        protected void onPostExecute(Exception e) {
            if (e != null) {
                Log.e(TAG, "Oops. Exception while syncing: " + e.getMessage());
            } else {
                Log.d(TAG, "Channels synced successfully.");
            }

            sInProgress = false;
            mSynchronizeDatabaseTask = null;
            jobFinished(mJobParameters, false);
        }

    }
}
