package com.liskovsoft.smartyoutubetv2.common.misc;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

public class RemoteControlReceiver extends BroadcastReceiver {
    private static final String TAG = RemoteControlReceiver.class.getSimpleName();
    private static final String[] BOOT_ACTIONS = {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            "android.intent.action.ACTION_BOOT_COMPLETED",
            Intent.ACTION_LOCKED_BOOT_COMPLETED
    };

    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Initializing remote control listener...");

        // Fix unload from the memory on some devices?
        // NOTE: Starting from Android 12 (api 31) foreground service with type 'connectedDevice' not supported
        // Use 'mediaPlayback' type instead
        try {
            Utils.updateRemoteControlService(context);
        } catch (Exception e) {
            // ForegroundServiceStartNotAllowedException: startForegroundService() not allowed due to mAllowStartForeground false (Android 12)
            e.printStackTrace();
        }

        // Auto-launch the app UI on device restart (not on every other action this receiver
        // listens to, e.g. SCREEN_ON/TIME_SET, which would be too aggressive).
        // Relies on the SYSTEM_ALERT_WINDOW permission exemption to start an Activity from a
        // background BroadcastReceiver context.
        if (isBootAction(intent) && context != null) {
            try {
                ViewManager.instance(context).moveAppToForeground();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isBootAction(Intent intent) {
        return intent != null && Helpers.equalsAny(intent.getAction(), BOOT_ACTIONS);
    }
}
