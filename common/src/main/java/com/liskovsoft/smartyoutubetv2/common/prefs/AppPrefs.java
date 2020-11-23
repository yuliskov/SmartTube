package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.sharedutils.prefs.SharedPreferencesBase;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem;

public class AppPrefs extends SharedPreferencesBase {
    private static final String TAG = AppPrefs.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static AppPrefs sInstance;
    private static final String VIDEO_FORMAT = "video_format";
    private static final String COMPLETED_ONBOARDING = "completed_onboarding";
    private static final String VIDEO_BUFFER_TYPE = "video_buffer_type";
    private static final String AUTO_FRAME_RATE_DATA = "auto_frame_rate_data";
    private static final String BACKUP_DATA = "backup_data";
    private static final String VIDEO_LOADER_DATA = "video_loader_data";
    private static final String STATE_UPDATER_DATA = "state_updater_data";
    private static final String STATE_UPDATER_PARAMS = "state_updater_params";
    private static final String PREFERRED_LANGUAGE_DATA = "preferred_language_data";
    private static final String VIEW_MANAGER_DATA = "view_manager_data";
    private static final String SUBTITLE_MANAGER_DATA = "subtitle_manager_data";
    private static final String MAIN_UI_DATA = "main_ui_data";
    private static final String UI_STYLE_DATA = "ui_style_data";
    private static final String VIDEO_PLAYER_DATA = "video_player_data";
    private static final String ACCOUNTS_DATA = "accounts_data";
    private static final String SEARCH_DATA = "search_data";
    private String mDefaultDisplayMode;
    private String mCurrentDisplayMode;

    private AppPrefs(Context context) {
        super(context);
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

    public void setFormat(FormatItem track) {
        putString(VIDEO_FORMAT + track.getType(), track.toString());
    }

    public FormatItem getFormat(int type, FormatItem defaultFormat) {
        FormatItem formatItem = ExoFormatItem.from(getString(VIDEO_FORMAT + type, null));
        return formatItem != null ? formatItem : defaultFormat;
    }

    public int getVideoBufferType(int defaultBufferType) {
        return getInt(VIDEO_BUFFER_TYPE, defaultBufferType);
    }

    public void setVideoBufferType(int bufferType) {
        putInt(VIDEO_BUFFER_TYPE, bufferType);
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

    public String getAfrData(String defaultData) {
        return getString(AUTO_FRAME_RATE_DATA, defaultData);
    }

    public void setAfrData(String afrData) {
        putString(AUTO_FRAME_RATE_DATA, afrData);
    }

    public void setBackupData(String backupData) {
        putString(BACKUP_DATA, backupData);
    }

    public String getBackupData() {
        return getString(BACKUP_DATA, null);
    }

    public int getVideoLoaderData(int defaultVal) {
        return getInt(VIDEO_LOADER_DATA, defaultVal);
    }

    public void setVideoLoaderData(int data) {
        putInt(VIDEO_LOADER_DATA, data);
    }

    public String getStateUpdaterItemsData() {
        return getString(STATE_UPDATER_DATA, null);
    }

    public void setStateUpdaterClipData(String data) {
        putString(STATE_UPDATER_DATA, data);
    }

    public String getStateUpdaterParams() {
        return getString(STATE_UPDATER_PARAMS, null);
    }

    public void setStateUpdaterParams(String data) {
        putString(STATE_UPDATER_PARAMS, data);
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

    public void setSubtitleManagerData(String data) {
        putString(SUBTITLE_MANAGER_DATA, data);
    }

    public String getSubtitleManagerData() {
        return getString(SUBTITLE_MANAGER_DATA, null);
    }

    public String getMainUIData() {
        return getString(MAIN_UI_DATA, null);
    }

    public void setMainUIData(String data) {
        putString(MAIN_UI_DATA, data);
    }

    public String getUIStyleData() {
        return getString(UI_STYLE_DATA, null);
    }

    public void setUIStyleData(String data) {
        putString(UI_STYLE_DATA, data);
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

    public void setSearchData(String data) {
        putString(SEARCH_DATA, data);
    }

    public String getSearchData() {
        return getString(SEARCH_DATA, null);
    }
}
