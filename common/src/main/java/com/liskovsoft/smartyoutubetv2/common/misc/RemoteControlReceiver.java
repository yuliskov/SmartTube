package com.liskovsoft.smartyoutubetv2.common.misc;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

public class RemoteControlReceiver extends BroadcastReceiver {
    private static final String TAG = RemoteControlReceiver.class.getSimpleName();

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
    }
}
