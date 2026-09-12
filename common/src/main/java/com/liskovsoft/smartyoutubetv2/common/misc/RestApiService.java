package com.liskovsoft.smartyoutubetv2.common.misc;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.rest.RestApiManager;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

public class RestApiService extends Service {
    private static final String TAG = RestApiService.class.getSimpleName();
    private static final int NOTIFICATION_ID = RestApiService.class.hashCode();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind: %s", Helpers.toString(intent));
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(NOTIFICATION_ID, createNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: %s", Helpers.toString(intent));
        RestApiManager.instance(getApplicationContext()).start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        RestApiManager.instance(getApplicationContext()).stop();
        super.onDestroy();
    }

    private Notification createNotification() {
        String title = getString(R.string.rest_api_settings);
        String body = getString(R.string.background_service_started);

        return Utils.createNotification(
                getApplicationContext(),
                getApplicationInfo().icon,
                String.format("%s: %s", title, body),
                ViewManager.instance(getApplicationContext()).getRootActivity()
        );
    }
}
