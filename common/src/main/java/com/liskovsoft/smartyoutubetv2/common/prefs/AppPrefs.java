package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.sharedutils.prefs.SharedPreferencesBase;
import com.liskovsoft.smartyoutubetv2.common.R;

public class AppPrefs extends SharedPreferencesBase {
    private static final String TAG = AppPrefs.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static AppPrefs sInstance;
    private static final String COMPLETED_ONBOARDING = "completed_onboarding";
    private static final String BACKUP_DATA = "backup_data";
    private static final String STATE_UPDATER_DATA = "state_updater_data";
    private static final String VIEW_MANAGER_DATA = "view_manager_data";
    private String mBootResolution;

    private AppPrefs(Context context) {
        super(context, R.xml.app_prefs);
    }

    public static AppPrefs instance(Context context) {
        if (sInstance == null) {
            sInstance = new AppPrefs(context.getApplicationContext());
        }

        return sInstance;
    }

    public void setCompletedOnboarding(boolean completed) {
        putBoolean(COMPLETED_ONBOARDING, completed);
    }

    public boolean getCompletedOnboarding() {
        return getBoolean(COMPLETED_ONBOARDING, false);
    }

    public void setBootResolution(String resolution) {
        mBootResolution = resolution;
    }

    public String getBootResolution() {
        return mBootResolution;
    }

    public void setBackupData(String backupData) {
        putString(BACKUP_DATA, backupData);
    }

    public String getBackupData() {
        return getString(BACKUP_DATA, null);
    }

    public String getStateUpdaterData() {
        return getString(STATE_UPDATER_DATA, null);
    }

    public void setStateUpdaterData(String data) {
        putString(STATE_UPDATER_DATA, data);
    }

    public void setData(String key, String data) {
        putString(key, data);
    }

    public String getData(String key) {
        return getString(key, null);
    }

    public String getWebProxyUri() {
        return null;
    }

    public void setWebProxyUri(String uri) {
        
    }

    public boolean getWebProxyEnabled() {
        return false;
    }

    public void setWebProxyEnabled(boolean enabled) {
        
    }
}
