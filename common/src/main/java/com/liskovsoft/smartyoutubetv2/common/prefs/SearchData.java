package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.sharedutils.helpers.Helpers;

public class SearchData {
    @SuppressLint("StaticFieldLeak")
    private static SearchData sInstance;
    private final Context mContext;
    private final AppPrefs mAppPrefs;
    private boolean mIsInstantVoiceSearchEnabled;

    public SearchData(Context context) {
        mContext = context;
        mAppPrefs = AppPrefs.instance(mContext);
        restoreState();
    }

    public static SearchData instance(Context context) {
        if (sInstance == null) {
            sInstance = new SearchData(context.getApplicationContext());
        }

        return sInstance;
    }

    public void setInstantVoiceSearchEnabled(boolean enabled) {
        mIsInstantVoiceSearchEnabled = enabled;
        persistState();
    }

    public boolean isInstantVoiceSearchEnabled() {
        return mIsInstantVoiceSearchEnabled;
    }

    private void persistState() {
        mAppPrefs.setSearchData(Helpers.mergeObject(mIsInstantVoiceSearchEnabled));
    }

    private void restoreState() {
        String data = mAppPrefs.getSearchData();

        String[] split = Helpers.splitObject(data);

        mIsInstantVoiceSearchEnabled = Helpers.parseBoolean(split, 0, false);
    }
}
