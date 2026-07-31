package com.liskovsoft.smartyoutubetv2.common.misc;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;

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
    private PowerManager.WakeLock mWakeLock;

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
        // NOTE: it's impossible to hide notification on Android 9 and above
        // https://stackoverflow.com/questions/10962418/how-to-startforeground-without-showing-notification
        try {
            startForeground(NOTIFICATION_ID, createNotification());
        } catch (Exception e) {
            // NullPointerException: Attempt to read from field 'int com.android.server.am.UidRecord.curProcState' on a null object reference
            // ForegroundServiceStartNotAllowedException: Service.startForeground() not allowed due to mAllowStartForeground false (Android 14)
            e.printStackTrace();
        }

        acquireWakeLock();
    }

    @Override
    public void onDestroy() {
        releaseWakeLock();

        super.onDestroy();
    }

    /**
     * Keeps the CPU awake while this service is alive, so the background cast/remote-control
     * connection (Lounge API long-poll) survives when the TV screen goes to sleep.<br/>
     * Without this, the device may deep-sleep and drop the connection shortly after screen off,
     * making the TV disappear from the cast device list until it's woken up manually.
     */
    private void acquireWakeLock() {
        if (mWakeLock != null && mWakeLock.isHeld()) {
            return;
        }

        try {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

            if (powerManager != null) {
                mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG + ":remoteControl");
                mWakeLock.setReferenceCounted(false);
                mWakeLock.acquire();
                Log.d(TAG, "acquireWakeLock: partial wake lock acquired");
            }
        } catch (Exception e) {
            // SecurityException, missing WAKE_LOCK permission on some custom ROMs
            Log.e(TAG, "acquireWakeLock error: %s", e.getMessage());
            e.printStackTrace();
        }
    }

    private void releaseWakeLock() {
        Log.d(TAG, "releaseWakeLock: service is being destroyed, releasing wake lock");

        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
        }

        mWakeLock = null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: %s", Helpers.toString(intent));

        PlaybackPresenter.instance(getApplicationContext()); // init RemoteControlListener
        StreamReminderService.instance(getApplicationContext()).startStop(); // init reminder service

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
