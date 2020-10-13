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
    private String mDefaultDisplayMode;
    private String mCurrentDisplayMode;

    private AppPrefs(Context context) {
        super(context);
    }

    public static AppPrefs instance(Context context) {
        if (sInstance == null) {
            sInstance = new AppPrefs(context);
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
}
