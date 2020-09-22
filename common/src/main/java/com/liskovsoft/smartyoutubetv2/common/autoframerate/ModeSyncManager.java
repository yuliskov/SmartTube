package com.liskovsoft.smartyoutubetv2.common.autoframerate;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

public class ModeSyncManager {
    private static ModeSyncManager sInstance;
    private final AutoFrameRateHelper mAutoFrameRateHelper;
    private FormatItem mFormatItem;

    public ModeSyncManager(Activity activity) {
        mAutoFrameRateHelper = new AutoFrameRateHelper(activity);
    }

    public static ModeSyncManager instance(Activity activity) {
        if (sInstance == null) {
            sInstance = new ModeSyncManager(activity);
        }

        return sInstance;
    }

    public void save(FormatItem formatItem) {
        mFormatItem = formatItem;
    }

    public void restore(Activity activity) {
        if (mFormatItem != null) {
            mAutoFrameRateHelper.setActivity(activity);

            new Handler(Looper.myLooper()).postDelayed(() -> mAutoFrameRateHelper.apply(mFormatItem), 5_000);
        }
    }
}
