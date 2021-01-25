package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.sharedutils.helpers.Helpers;

public class ContentBlockData {
    private static final String CONTENT_BLOCK_DATA = "content_block_data";
    @SuppressLint("StaticFieldLeak")
    private static ContentBlockData sInstance;
    private final Context mContext;
    private final AppPrefs mAppPrefs;
    private boolean mIsSponsorBlockEnabled;

    private ContentBlockData(Context context) {
        mContext = context;
        mAppPrefs = AppPrefs.instance(mContext);
        restoreState();
    }

    public static ContentBlockData instance(Context context) {
        if (sInstance == null) {
            sInstance = new ContentBlockData(context.getApplicationContext());
        }

        return sInstance;
    }

    public boolean isSponsorBlockEnabled() {
        return mIsSponsorBlockEnabled;
    }

    public void setSponsorBlockEnabled(boolean enabled) {
        mIsSponsorBlockEnabled = enabled;
        persistData();
    }

    private void restoreState() {
        String data = mAppPrefs.getData(CONTENT_BLOCK_DATA);

        String[] split = Helpers.splitObjectLegacy(data);

        mIsSponsorBlockEnabled = Helpers.parseBoolean(split, 0, false);
    }

    private void persistData() {
        mAppPrefs.setData(CONTENT_BLOCK_DATA, Helpers.mergeObject(mIsSponsorBlockEnabled));
    }
}
