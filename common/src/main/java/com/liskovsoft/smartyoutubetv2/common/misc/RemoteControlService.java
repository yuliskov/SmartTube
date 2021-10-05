package com.liskovsoft.smartyoutubetv2.common.misc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build.VERSION;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;

public class RemoteControlService extends Service {
    private static final String TAG = RemoteControlService.class.getSimpleName();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind: %s", Helpers.toString(intent));

        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // https://stackoverflow.com/questions/46445265/android-8-0-java-lang-illegalstateexception-not-allowed-to-start-service-inten
        if (VERSION.SDK_INT >= 26) {
            startForeground(1, createNotification());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: %s", Helpers.toString(intent));

        PlaybackPresenter.instance(getApplicationContext()); // init RemoteControlListener

        //showNotification();

        return START_STICKY;
    }

    private void showNotification() {
        int NOTIFICATION_ID = 12345;
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, createNotification());
    }

    private Notification createNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.generic_channels)
                        .setContentTitle(getApplicationContext().getString(R.string.device_link_enabled))
                        .setContentText(getApplicationContext().getString(R.string.device_link_enabled));

        Intent targetIntent = new Intent(this, ViewManager.instance(getApplicationContext()).getRootActivity());
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, targetIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(contentIntent);

        if (VERSION.SDK_INT >= 26) {
            String channelId = getApplicationContext().getPackageName();
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    getApplicationContext().getString(R.string.search_label),
                    NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
            builder.setChannelId(channelId);
        }

        return builder.build();
    }
}
