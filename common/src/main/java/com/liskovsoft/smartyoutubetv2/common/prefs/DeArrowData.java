package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataSaverBase;

public class DeArrowData extends DataSaverBase {
    private static DeArrowData sInstance;

    private DeArrowData(Context context) {
        super(context);
    }

    public static DeArrowData instance(Context context) {
        if (sInstance == null) {
            sInstance = new DeArrowData(context);
        }

        return sInstance;
    }

    public void enableDeArrow(boolean enable) {
        setBoolean(0, enable);
    }

    public boolean isDeArrowEnabled() {
        return getBoolean(0, false);
    }

    public void replaceTitles(boolean replace) {
        setBoolean(1, replace);
    }

    public boolean isReplaceTitlesEnabled() {
        return getBoolean(1, false);
    }

    public void replaceThumbnails(boolean replace) {
        setBoolean(2, replace);
    }

    public boolean isReplaceThumbnailsEnabled() {
        return getBoolean(2, false);
    }
}
