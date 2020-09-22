package com.liskovsoft.smartyoutubetv2.common.autoframerate;

import android.app.Activity;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.internal.DisplaySyncHelper;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.internal.DisplaySyncHelperAlt;

public class ModeSyncManager {
    private static ModeSyncManager sInstance;
    private final DisplaySyncHelper mDisplaySyncHelper;

    public ModeSyncManager(Activity activity) {
        mDisplaySyncHelper = new DisplaySyncHelperAlt(activity);
    }

    public static ModeSyncManager instance(Activity activity) {
        if (sInstance == null) {
            sInstance = new ModeSyncManager(activity);
        }

        return sInstance;
    }

    public void save(Activity activity) {
        mDisplaySyncHelper.setContext(activity);
        mDisplaySyncHelper.saveCurrentState();
    }

    public void restore(Activity activity) {
        mDisplaySyncHelper.setContext(activity);
        mDisplaySyncHelper.restoreCurrentState(activity.getWindow());
    }
}
