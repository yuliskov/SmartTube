package com.liskovsoft.smartyoutubetv2.common.misc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;

/**
 * This service isn't used at all! It can be safely removed in the future.
 */
public class BackgroundPlaybackService extends Service {
    private static final String TAG = BackgroundPlaybackService.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();

        if (Build.VERSION.SDK_INT >= 26) {
            String CHANNEL_ID = "my_channel_01";
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Channel human readable title",
                    NotificationManager.IMPORTANCE_DEFAULT);

            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("")
                    .setContentText("").build();

            startForeground(1, notification);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind: %s", Helpers.toString(intent));

        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: %s", Helpers.toString(intent));

        return super.onStartCommand(intent, flags, startId);
    }

    public static void start(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            // Fake service to prevent the app from destroying
            Intent serviceIntent = new Intent(context, BackgroundPlaybackService.class);
            context.startForegroundService(serviceIntent);
        }
    }

    public static void stop(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            // Fake service to prevent the app from destroying
            Intent serviceIntent = new Intent(context, BackgroundPlaybackService.class);
            context.stopService(serviceIntent);
        }
    }
}
