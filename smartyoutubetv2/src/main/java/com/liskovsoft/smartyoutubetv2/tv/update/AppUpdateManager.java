package com.liskovsoft.smartyoutubetv2.tv.update;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.appupdatechecker2.AppUpdateChecker;
import com.liskovsoft.appupdatechecker2.AppUpdateCheckerListener;

import java.util.List;

public class AppUpdateManager implements AppUpdateCheckerListener {
    private static final String UPDATE_MANIFEST_URL = "https://github.com/yuliskov/SmartYouTubeTV/releases/download/stable/smartyoutubetv.json";
    @SuppressLint("StaticFieldLeak")
    private static AppUpdateManager sInstance;
    private final Context mContext;
    private final AppUpdateChecker mUpdateChecker;

    public AppUpdateManager(Context context) {
        mContext = context;
        mUpdateChecker = new AppUpdateChecker(mContext, this);
    }

    public static AppUpdateManager instance(Context context) {
        if (sInstance == null) {
            sInstance = new AppUpdateManager(context.getApplicationContext());
        }

        return sInstance;
    }

    public void unhold() {
        sInstance = null;
    }

    public void start() {
        mUpdateChecker.forceCheckForUpdates(UPDATE_MANIFEST_URL);
    }

    @Override
    public int onUpdateFound(List<String> changelog) {
        return AppUpdateCheckerListener.ACTION_INSTALL;
    }

    @Override
    public void onError(Exception error) {
        // NOP
    }
}
