package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.sharedutils.helpers.Helpers;

public class ProviderData {
    private static final String PROVIDER_DATA = "provider_data";
    @SuppressLint("StaticFieldLeak")
    private static ProviderData sInstance;
    private final Context mContext;
    private final AppPrefs mAppPrefs;
    private boolean mIsInstantVoiceSearchEnabled;

    private ProviderData(Context context) {
        mContext = context;
        mAppPrefs = AppPrefs.instance(mContext);
        restoreData();
    }

    public static ProviderData instance(Context context) {
        if (sInstance == null) {
            sInstance = new ProviderData(context.getApplicationContext());
        }

        return sInstance;
    }

    public void setInstantVoiceSearchEnabled(boolean enabled) {
        mIsInstantVoiceSearchEnabled = enabled;
        persistData();
    }

    public boolean isInstantVoiceSearchEnabled() {
        return mIsInstantVoiceSearchEnabled;
    }

    private void restoreData() {
        String data = mAppPrefs.getData(PROVIDER_DATA);

        String[] split = Helpers.splitPrefs(data);

        mIsInstantVoiceSearchEnabled = Helpers.parseBoolean(split, 0, false);
    }

    private void persistData() {
        mAppPrefs.setData(PROVIDER_DATA, Helpers.mergePrefs(mIsInstantVoiceSearchEnabled));
    }
}
