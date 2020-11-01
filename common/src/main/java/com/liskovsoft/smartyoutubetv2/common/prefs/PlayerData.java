package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.sharedutils.helpers.Helpers;

public class PlayerData {
    @SuppressLint("StaticFieldLeak")
    private static PlayerData sInstance;
    private final Context mContext;
    private final AppPrefs mPrefs;
    private boolean mIsShowUIOnPauseEnabled = true;
    private boolean mIsPauseOnOKEnabled;

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
        mIsShowUIOnPauseEnabled = enable;
        persistData();
    }

    public void pauseOnOK(boolean enable) {
        mIsPauseOnOKEnabled = enable;
        persistData();
    }

    public boolean isPauseOnOKEnabled() {
        return mIsPauseOnOKEnabled;
    }

    public boolean isShowUIOnPauseEnabled() {
        return mIsShowUIOnPauseEnabled;
    }

    private void restoreData() {
        String data = mPrefs.getPlayerData();

        if (data != null) {
            String[] split = data.split(",");

            mIsShowUIOnPauseEnabled = Helpers.parseBoolean(split, 0);
            mIsPauseOnOKEnabled = Helpers.parseBoolean(split, 1);
        }
    }

    private void persistData() {
        mPrefs.setPlayerData(String.format("%s,%s", mIsShowUIOnPauseEnabled, mIsPauseOnOKEnabled));
    }
}
