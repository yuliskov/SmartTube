package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.sharedutils.helpers.Helpers;

public class DeviceLinkData extends DataChangeBase {
    @SuppressLint("StaticFieldLeak")
    private static DeviceLinkData sInstance;
    private final Context mContext;
    private final AppPrefs mAppPrefs;
    private boolean mIsDeviceLinkEnabled;

    private DeviceLinkData(Context context) {
        mContext = context;
        mAppPrefs = AppPrefs.instance(mContext);
        restoreState();
    }

    public static DeviceLinkData instance(Context context) {
        if (sInstance == null) {
            sInstance = new DeviceLinkData(context.getApplicationContext());
        }

        return sInstance;
    }

    public void enableDeviceLink(boolean select) {
        mIsDeviceLinkEnabled = select;
        persistState();
    }

    public boolean isDeviceLinkEnabled() {
        return mIsDeviceLinkEnabled;
    }

    protected void persistState() {
        mAppPrefs.setDeviceLinkData(Helpers.mergeObject(mIsDeviceLinkEnabled));

        super.persistState();
    }

    private void restoreState() {
        String data = mAppPrefs.getDeviceLinkData();

        String[] split = Helpers.splitObjectLegacy(data);

        mIsDeviceLinkEnabled = Helpers.parseBoolean(split, 0, true);
    }
}
