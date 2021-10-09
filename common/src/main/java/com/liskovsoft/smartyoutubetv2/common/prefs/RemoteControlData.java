package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.sharedutils.helpers.Helpers;

public class RemoteControlData extends DataChangeBase {
    private static final String DEVICE_LINK_DATA = "device_link_data";
    @SuppressLint("StaticFieldLeak")
    private static RemoteControlData sInstance;
    private final Context mContext;
    private final AppPrefs mAppPrefs;
    private boolean mIsDeviceLinkEnabled;
    private boolean mIsRunInBackgroundEnabled;

    private RemoteControlData(Context context) {
        mContext = context;
        mAppPrefs = AppPrefs.instance(mContext);
        restoreState();
    }

    public static RemoteControlData instance(Context context) {
        if (sInstance == null) {
            sInstance = new RemoteControlData(context.getApplicationContext());
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

    public void enableRunInBackground(boolean select) {
        mIsRunInBackgroundEnabled = select;
        persistState();
    }

    public boolean isRunInBackgroundEnabled() {
        return mIsRunInBackgroundEnabled;
    }

    private void restoreState() {
        String data = mAppPrefs.getData(DEVICE_LINK_DATA);

        String[] split = Helpers.splitObjectLegacy(data);

        mIsDeviceLinkEnabled = Helpers.parseBoolean(split, 1, true);
        mIsRunInBackgroundEnabled = Helpers.parseBoolean(split, 2, true);
    }

    protected void persistState() {
        mAppPrefs.setData(DEVICE_LINK_DATA, Helpers.mergeObject(null, mIsDeviceLinkEnabled, mIsRunInBackgroundEnabled));

        super.persistState();
    }
}
