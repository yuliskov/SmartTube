package com.liskovsoft.smartyoutubetv2.common.playback.exoplayer;

import android.content.Context;

public class ExoStateManager {
    private static ExoStateManager sInstance;
    private final Context mContext;

    private ExoStateManager(Context context) {
        mContext = context;
    }

    public static ExoStateManager instance(Context context) {
        if (sInstance == null) {
            sInstance = new ExoStateManager(context.getApplicationContext());
        }

        return sInstance;
    }
}
