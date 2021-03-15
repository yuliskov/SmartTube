package com.liskovsoft.smartyoutubetv2.tv;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.liskovsoft.sharedutils.mylogger.Log;

import java.util.Locale;

public class Analytics {
    private final static String ACTION_ACTIVITY_STARTED = "ACTION_ACTIVITY_STARTED";

    public static void sendActivityStarted(@NonNull Context context, String activityName) {
        Log.d("Analytics", "sendActivityStarted: " + activityName);
        Intent intent = new Intent(ACTION_ACTIVITY_STARTED);
        intent.putExtra("app_name", BuildConfig.APPLICATION_ID);
        intent.putExtra("app_version",
                String.format(Locale.US, "%s-%d",
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE));
        intent.putExtra("activity_name", activityName);
        context.sendBroadcast(intent);
    }
}