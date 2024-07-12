package com.liskovsoft.leanbackassistant.channels;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;

/*
 * The receiver is automatically invoked when the app is first installed from Play
 * Store. Use this as a run once signal to publish the app's first channel and add others to
 * "Customize Channels" menu.
 *
 * Test in your app by "adb shell am broadcast -a android.media.tv.action.INITIALIZE_PROGRAMS
 *      -n your.package.name/.YourReceiverName"
 *
 * Test in this sample app by "adb shell am broadcast -a android.media.tv.action.INITIALIZE_PROGRAMS
 *      -n com.google.android.tvhomescreenchannels/.RunOnInstallReceiver"
 */
public class UpdateChannelsReceiver extends BroadcastReceiver {
    private static final String TAG = UpdateChannelsReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Synchronizing database");

        if (Helpers.isATVChannelsSupported(context) || Helpers.isATVRecommendationsSupported(context)) {
            UpdateChannelsWorker.schedule(context);
            //UpdateChannelsJobService.schedule(context);
        }
    }
}