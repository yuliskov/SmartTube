package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.sharedutils.helpers.Helpers;

public class MainUIData {
    @SuppressLint("StaticFieldLeak")
    private static MainUIData sInstance;
    private final Context mContext;
    private final AppPrefs mPrefs;
    private boolean mIsAnimatedPreviewsEnabled = true;

    public MainUIData(Context context) {
        mContext = context;
        mPrefs = AppPrefs.instance(context);
        restoreData();
    }

    public static MainUIData instance(Context context) {
        if (sInstance == null) {
            sInstance = new MainUIData(context.getApplicationContext());
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
        String data = mPrefs.getMainUIData();

        if (data != null) {
            String[] split = data.split(",");

            mIsAnimatedPreviewsEnabled = Helpers.parseBoolean(split, 0);
        }
    }

    private void persistData() {
        mPrefs.setMainUIData(String.format("%s", mIsAnimatedPreviewsEnabled));
    }
}
