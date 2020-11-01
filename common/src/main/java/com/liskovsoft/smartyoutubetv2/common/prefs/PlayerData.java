package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.sharedutils.helpers.Helpers;

public class PlayerData {
    @SuppressLint("StaticFieldLeak")
    private static PlayerData sInstance;
    private final Context mContext;
    private final AppPrefs mPrefs;
    private boolean mIsUIShownOnPause = true;

    public PlayerData(Context context) {
        mContext = context;
        mPrefs = AppPrefs.instance(context);
        restoreData();
    }

    public static PlayerData instance(Context context) {
        if (sInstance == null) {
            sInstance = new PlayerData(context.getApplicationContext());
        }

        return sInstance;
    }

    public void showUIOnPause(boolean enable) {
        mIsUIShownOnPause = enable;
        persistData();
    }

    public boolean isUIShownOnPause() {
        return mIsUIShownOnPause;
    }

    private void restoreData() {
        String data = mPrefs.getPlayerData();

        if (data != null) {
            String[] split = data.split(",");

            mIsUIShownOnPause = Helpers.parseBoolean(split, 0);
        }
    }

    private void persistData() {
        mPrefs.setPlayerData(String.format("%s", mIsUIShownOnPause));
    }
}
