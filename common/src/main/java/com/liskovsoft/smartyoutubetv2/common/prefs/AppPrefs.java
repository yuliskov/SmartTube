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
    private static final String PREFERRED_LANGUAGE_DATA = "preferred_language_data";
    private static final String VIEW_MANAGER_DATA = "view_manager_data";
    private static final String MAIN_UI_DATA = "main_ui_data";
    private static final String VIDEO_PLAYER_DATA = "video_player_data";
    private static final String ACCOUNTS_DATA = "accounts_data";
    private static final String DEVICE_LINK_DATA = "device_link_data";
    private static final String SEARCH_DATA = "search_data";
    private String mDefaultDisplayMode;
    private String mCurrentDisplayMode;

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

    public void setDefaultDisplayMode(String mode) {
        mDefaultDisplayMode = mode;
    }

    public String getDefaultDisplayMode() {
        return mDefaultDisplayMode;
    }

    public void setCurrentDisplayMode(String mode) {
        mCurrentDisplayMode = mode;
    }

    public String getCurrentDisplayMode() {
        return mCurrentDisplayMode;
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

    public void setPreferredLanguage(String langData) {
        putString(PREFERRED_LANGUAGE_DATA, langData);
    }

    public String getPreferredLanguage() {
        return getString(PREFERRED_LANGUAGE_DATA, null);
    }

    public void setViewManagerData(String data) {
        putString(VIEW_MANAGER_DATA, data);
    }

    public String getViewManagerData() {
        return getString(VIEW_MANAGER_DATA, null);
    }

    public String getMainUIData() {
        return getString(MAIN_UI_DATA, null);
    }

    public void setMainUIData(String data) {
        putString(MAIN_UI_DATA, data);
    }

    public String getPlayerData() {
        return getString(VIDEO_PLAYER_DATA, null);
    }

    public void setPlayerData(String data) {
        putString(VIDEO_PLAYER_DATA, data);
    }

    public void setAccountsData(String data) {
        putString(ACCOUNTS_DATA, data);
    }

    public String getAccountsData() {
        return getString(ACCOUNTS_DATA, null);
    }

    public void setDeviceLinkData(String data) {
        putString(DEVICE_LINK_DATA, data);
    }

    public String getDeviceLinkData() {
        return getString(DEVICE_LINK_DATA, null);
    }

    public void setSearchData(String data) {
        putString(SEARCH_DATA, data);
    }

    public String getSearchData() {
        return getString(SEARCH_DATA, null);
    }
}
