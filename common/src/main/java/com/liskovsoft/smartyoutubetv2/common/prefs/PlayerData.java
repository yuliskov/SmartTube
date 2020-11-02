package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.sharedutils.helpers.Helpers;

public class PlayerData {
    public static final int ONLY_UI = 0;
    public static final int UI_AND_PAUSE = 1;
    public static final int ONLY_PAUSE = 2;
    public static final int AUTO_HIDE_NEVER = 0;
    @SuppressLint("StaticFieldLeak")
    private static PlayerData sInstance;
    private final Context mContext;
    private final AppPrefs mPrefs;
    private int mOKButtonBehavior;
    private int mUIHideTimeoutSec;

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

    public void setOKButtonBehavior(int option) {
        mOKButtonBehavior = option;
        persistData();
    }

    public int getOKButtonBehavior() {
        return mOKButtonBehavior;
    }

    public void setUIHideTimoutSec(int timoutSec) {
        mUIHideTimeoutSec = timoutSec;
        persistData();
    }

    public int getUIHideTimoutSec() {
        return mUIHideTimeoutSec;
    }

    private void restoreData() {
        String data = mPrefs.getPlayerData();

        if (data != null) {
            String[] split = data.split(",");

            mOKButtonBehavior = Helpers.parseInt(split, 0, ONLY_UI);
            mUIHideTimeoutSec = Helpers.parseInt(split, 1, 3);
        }
    }

    private void persistData() {
        mPrefs.setPlayerData(String.format("%s,%s", mOKButtonBehavior, mUIHideTimeoutSec));
    }
}
