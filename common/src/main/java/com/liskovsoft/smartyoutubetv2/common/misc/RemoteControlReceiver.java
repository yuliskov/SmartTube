package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class RemoteControlReceiver extends BroadcastReceiver {
    private static final String TAG = RemoteControlReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Initializing remote control listener...");

        startService(context);

        startWorkRequest(context);

        PlaybackPresenter.instance(context); // init RemoteControlListener
    }

    private void startService(Context context) {
        // Fake service to prevent the app from destroying
        Intent serviceIntent = new Intent(context, RemoteControlService.class);
        context.startService(serviceIntent);
    }

    private void startWorkRequest(Context context) {
        WorkRequest workRequest =
                new PeriodicWorkRequest.Builder(
                        RemoteControlWorker.class, 30, TimeUnit.MINUTES
                ).build();

        WorkManager
                .getInstance(context)
                .enqueue(workRequest);
    }
}
