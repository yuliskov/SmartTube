package com.liskovsoft.smartyoutubetv2.tv.update;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.appupdatechecker2.AppUpdateChecker;

public class AppUpdateManager {
    private static final String UPDATE_MANIFEST_URL = "https://github.com/yuliskov/SmartYouTubeTV/releases/download/stable/smartyoutubetv.json";
    @SuppressLint("StaticFieldLeak")
    private static AppUpdateManager sInstance;
    private final Context mContext;
    private final AppUpdateChecker mUpdateChecker;

    public AppUpdateManager(Context context) {
        mContext = context;
        mUpdateChecker = new AppUpdateChecker(mContext);
    }

    public static AppUpdateManager instance(Context context) {
        if (sInstance == null) {
            sInstance = new AppUpdateManager(context.getApplicationContext());
        }

        return sInstance;
    }

    public void checkForUpdates() {
        mUpdateChecker.forceCheckForUpdates(UPDATE_MANIFEST_URL);
    }

    public void unhold() {
        sInstance = null;
    }
}
