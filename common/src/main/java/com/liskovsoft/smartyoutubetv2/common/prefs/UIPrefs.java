package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.sharedutils.helpers.Helpers;

public class UIPrefs {
    @SuppressLint("StaticFieldLeak")
    private static UIPrefs sInstance;
    private final Context mContext;
    private final AppPrefs mPrefs;
    private boolean mIsAnimatedPreviewsEnabled = true;

    public UIPrefs(Context context) {
        mContext = context;
        mPrefs = AppPrefs.instance(context);
        restoreData();
    }

    public static UIPrefs instance(Context context) {
        if (sInstance == null) {
            sInstance = new UIPrefs(context.getApplicationContext());
        }

        return sInstance;
    }

    public void enableAnimatedPreviews(boolean enable) {
        mIsAnimatedPreviewsEnabled = enable;
        persistData();
    }

    public boolean isAnimatedPreviewsEnabled() {
        return mIsAnimatedPreviewsEnabled;
    }

    private void restoreData() {
        String data = mPrefs.getUIData();

        if (data != null) {
            String[] split = data.split(",");

            mIsAnimatedPreviewsEnabled = Helpers.parseBoolean(split, 0);
        }
    }

    private void persistData() {
        mPrefs.setUIData(String.format("%s", mIsAnimatedPreviewsEnabled));
    }
}
