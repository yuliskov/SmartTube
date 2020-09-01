package com.liskovsoft.smartyoutubetv2.common.playback.player;

import android.content.Context;

public class PlaybackManager {
    private static PlaybackManager sInstance;
    private final Context mContext;

    private PlaybackManager(Context context) {
        mContext = context;
    }

    public static PlaybackManager instance(Context context) {
        if (sInstance == null) {
            sInstance = new PlaybackManager(context.getApplicationContext());
        }

        return sInstance;
    }
}
