package com.liskovsoft.smartyoutubetv2.common.app.models.update;

import android.annotation.SuppressLint;
import android.content.Context;

import java.util.List;

public class AppUpdateManager implements IAppUpdateManager {
    @SuppressLint("StaticFieldLeak")
    private static AppUpdateManager sInstance;

    public AppUpdateManager(Context context) {

    }

    public static IAppUpdateManager instance(Context context) {
        if (sInstance == null) {
            sInstance = new AppUpdateManager(context.getApplicationContext());
        }

        return sInstance;
    }

    public void unhold() {
        sInstance = null;
    }

    public void start(boolean forceCheck) {

    }

    public void onUpdateFound(String versionName, List<String> changelog, String apkPath) {
    }

    public void onError(Exception error) {

    }

    public void enableUpdateCheck(boolean b) {

    }

    public boolean isUpdateCheckEnabled() {
        return false;
    }
}
