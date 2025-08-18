package com.liskovsoft.smartyoutubetv2.common.misc;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.Build.VERSION;
import android.os.IBinder;
import androidx.annotation.Nullable;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

public class RemoteControlService extends Service {
    private static final String TAG = RemoteControlService.class.getSimpleName();
    private static final int NOTIFICATION_ID = RemoteControlService.class.hashCode();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind: %s", Helpers.toString(intent));

        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        //// https://stackoverflow.com/questions/46445265/android-8-0-java-lang-illegalstateexception-not-allowed-to-start-service-inten
        //if (VERSION.SDK_INT >= 26) {
        //    // NOTE: it's impossible to hide notification on Android 9 and above
        //    // https://stackoverflow.com/questions/10962418/how-to-startforeground-without-showing-notification
        //    startForeground(NOTIFICATION_ID, createNotification());
        //}

        // https://stackoverflow.com/questions/46445265/android-8-0-java-lang-illegalstateexception-not-allowed-to-start-service-inten
        // NOTE: it's impossible to hide notification on Android 9 and above
        // https://stackoverflow.com/questions/10962418/how-to-startforeground-without-showing-notification
        try {
            startForeground(NOTIFICATION_ID, createNotification());
        } catch (NullPointerException e) {
            // NullPointerException: Attempt to read from field 'int com.android.server.am.UidRecord.curProcState' on a null object reference
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: %s", Helpers.toString(intent));

        PlaybackPresenter.instance(getApplicationContext()); // init RemoteControlListener
        StreamReminderService.instance(getApplicationContext()).start(); // init reminder service

        return START_STICKY;
    }

    private Notification createNotification() {
        String remoteControl = getString(R.string.settings_remote_control);
        String serviceStarted = getString(R.string.background_service_started);

        return Utils.createNotification(
                getApplicationContext(),
                getApplicationInfo().icon,
                String.format("%s: %s", remoteControl, serviceStarted),
                ViewManager.instance(getApplicationContext()).getRootActivity());
    }
}
