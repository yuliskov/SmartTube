package com.liskovsoft.smartyoutubetv2.common.playback.player;

import android.content.Context;

public class PlayerManager {
    private static PlayerManager sInstance;
    private final Context mContext;

    private PlayerManager(Context context) {
        mContext = context;
    }

    public static PlayerManager instance(Context context) {
        if (sInstance == null) {
            sInstance = new PlayerManager(context.getApplicationContext());
        }

        return sInstance;
    }
}
