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
    private boolean mIsFinishOnDisconnectEnabled;
    private boolean mIsConnectMessagesEnabled;

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
        // Merge device link and background service (saves memory)
        return mIsDeviceLinkEnabled;
    }

    public void enableFinishOnDisconnect(boolean enable) {
        mIsFinishOnDisconnectEnabled = enable;
        persistState();
    }

    public boolean isFinishOnDisconnectEnabled() {
        return mIsFinishOnDisconnectEnabled;
    }

    public void enableConnectMessages(boolean enable) {
        mIsConnectMessagesEnabled = enable;
        persistState();
    }

    public boolean isConnectMessagesEnabled() {
        return mIsConnectMessagesEnabled;
    }

    private void restoreState() {
        String data = mAppPrefs.getData(DEVICE_LINK_DATA);

        String[] split = Helpers.splitObjectLegacy(data);

        // null
        // null
        mIsDeviceLinkEnabled = Helpers.parseBoolean(split, 2, false);
        mIsFinishOnDisconnectEnabled = Helpers.parseBoolean(split, 3, false);
        mIsConnectMessagesEnabled = Helpers.parseBoolean(split, 4, false);
    }

    protected void persistState() {
        mAppPrefs.setData(DEVICE_LINK_DATA, Helpers.mergeObject(null, null, mIsDeviceLinkEnabled, mIsFinishOnDisconnectEnabled, mIsConnectMessagesEnabled));

        super.persistState();
    }
}
