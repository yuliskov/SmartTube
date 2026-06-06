package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataSaverBase;

public class NetworkData extends DataSaverBase {
    private static NetworkData sInstance;

    private NetworkData(Context context) {
        super(context);
    }

    public static NetworkData instance(Context context) {
        if (sInstance == null) {
            sInstance = new NetworkData(context);
        }

        return sInstance;
    }

    public boolean isConscryptEnabled() {
        return getBoolean(0, false);
    }

    public void setConscryptEnabled(boolean enable) {
        setBoolean(0, enable);
    }
}
