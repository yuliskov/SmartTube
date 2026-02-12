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

    public boolean isDeArrowEnabled() {
        return getBoolean(0);
    }

    public void setDeArrowEnabled(boolean enable) {
        setBoolean(0, enable);
    }

    public boolean isReplaceTitlesEnabled() {
        return getBoolean(1);
    }

    public void setReplaceTitlesEnabled(boolean enable) {
        setBoolean(1, enable);
    }

    public boolean isReplaceThumbnailsEnabled() {
        return getBoolean(2);
    }

    public void setReplaceThumbnailsEnabled(boolean replace) {
        setBoolean(2, replace);
    }
}
