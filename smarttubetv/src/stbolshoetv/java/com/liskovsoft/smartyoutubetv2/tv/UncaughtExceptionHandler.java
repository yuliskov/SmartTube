package com.liskovsoft.smartyoutubetv2.tv;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Locale;

/**
 * Created by vadim on 11.12.20.
 */

public class UncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
    private static final String TAG = "UncaughtExceptionHandler";
    private static final String ACTION_APP_UNCAUGHT_EXCEPTION = "ACTION_APP_UNCAUGHT_EXCEPTION";
    private static final String MY_PROCESS_ID = Integer.toString(android.os.Process.myPid());

    private final Thread.UncaughtExceptionHandler mPreviousHandler;
    private final Context mContext;

    public UncaughtExceptionHandler(Context context, boolean chained) {
        mContext = context;
        if (chained) {
            mPreviousHandler = Thread.getDefaultUncaughtExceptionHandler();
        } else {
            mPreviousHandler = null;
        }
    }

    @Override
    public void uncaughtException(Thread thread, Throwable exception) {
        Log.e(TAG, "===UncaughtException===");
        exception.printStackTrace();
        Log.e(TAG, "=== ===");

        Intent intent = new Intent(ACTION_APP_UNCAUGHT_EXCEPTION);
        intent.putExtra("app", BuildConfig.APPLICATION_ID);
        intent.putExtra("app_version",
                String.format(Locale.US, "%s-%d",
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE));
        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(exception.getLocalizedMessage()).append("\n");
        for (int i = 0; i < exception.getStackTrace().length; i++) {
            stringBuilder.append(exception.getStackTrace()[i]).append("\n");
        }
        intent.putExtra("trace", stringBuilder.toString());
        intent.putExtra("name", exception.getClass().getName());
        intent.putExtra("type", ACTION_APP_UNCAUGHT_EXCEPTION);
        intent.putExtra("logs", LogUtil.readLog());
        mContext.sendBroadcast(intent);

        if (mPreviousHandler != null) {
            mPreviousHandler.uncaughtException(thread, exception);
        } else {
            System.exit(2);
        }
    }

    public static StringBuilder getLog() {
        StringBuilder builder = new StringBuilder();

        try {
            String[] command = new String[]{"logcat", "-d", "-v", "threadtime"};

            Process process = Runtime.getRuntime().exec(command);

            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.contains(MY_PROCESS_ID)) {
                    builder.append(line);
                    //Code here
                }
            }
        } catch (IOException ex) {
            Log.e(TAG, "getLog failed", ex);
        }

        return builder;
    }
}