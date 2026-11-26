package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;
import android.os.Build.VERSION;

import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataSaverBase;

public class NetworkData extends DataSaverBase {
    private static NetworkData sInstance;

    private NetworkData(Context context) {
        super(context, true);
    }

    public static NetworkData instance(Context context) {
        if (sInstance == null) {
            sInstance = new NetworkData(context);
        }

        return sInstance;
    }

    public boolean isConscryptEnabled() {
        // Android 4.x: no TLS 1.2 by default, no GCM/SHA-2 suites -> GitHub rejects the handshake
        // (breaks "Check for updates"). Other hosts work fine. Not needed on Android 5.0+.
        return getBoolean(0, VERSION.SDK_INT < 21);
    }

    public void setConscryptEnabled(boolean enable) {
        setBoolean(0, enable);
    }
}
