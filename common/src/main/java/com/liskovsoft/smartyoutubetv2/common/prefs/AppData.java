package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.sharedutils.helpers.Helpers;

public class AppData {
    private static final String APP_DATA = "app_data";
    @SuppressLint("StaticFieldLeak")
    private static AppData sInstance;
    private final AppPrefs mPrefs;
    private String mPreferredSource;

    private AppData(Context context) {
        mPrefs = AppPrefs.instance(context);
        restoreData();
    }

    public static AppData instance(Context context) {
        if (sInstance == null) {
            sInstance = new AppData(context.getApplicationContext());
        }

        return sInstance;
    }

    public void setPreferredSource(String url) {
        mPreferredSource = url;

        persistData();
    }

    public String getPreferredSource() {
        return mPreferredSource;
    }

    private void restoreData() {
        String data = mPrefs.getData(APP_DATA);

        String[] split = Helpers.splitObjectLegacy(data);

        mPreferredSource = Helpers.parseStr(split, 0);
    }

    private void persistData() {
        mPrefs.setData(APP_DATA, Helpers.mergeObject(
                mPreferredSource
        ));
    }
}
