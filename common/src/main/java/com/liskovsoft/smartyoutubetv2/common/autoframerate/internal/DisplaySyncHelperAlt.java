package com.liskovsoft.smartyoutubetv2.common.autoframerate.internal;

import android.app.Activity;
import android.view.Window;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.internal.DisplayHolder.Mode;

import java.util.HashMap;

public class DisplaySyncHelperAlt extends DisplaySyncHelper {
    private static final String TAG = DisplaySyncHelperAlt.class.getSimpleName();
    private static final float UI_LAGGING_THRESHOLD = 30;

    public DisplaySyncHelperAlt(Activity context) {
        super(context);
    }

    @Override
    protected HashMap<Integer, int[]> getRateMapping() {
        return getFHDRateMapping();
    }

    /**
     * ExoPlayer reports wrong for 60 and 30 fps formats.<br/>
     * Do workarounds: 60 => 59.94, 30 => 59.94
     */
    private HashMap<Integer, int[]> getFHDRateMapping() {
        HashMap<Integer, int[]> relatedRates = new HashMap<>();
        relatedRates.put(1500, new int[]{6000, 3000});
        relatedRates.put(2397, new int[]{4794, 4800, 2397, 2400});
        relatedRates.put(2400, new int[]{4800, 2400});
        relatedRates.put(2497, new int[]{4994, 5000, 2497, 2500});
        relatedRates.put(2500, new int[]{5000, 2500});
        relatedRates.put(2997, new int[]{5994, 6000, 2997, 3000});
        relatedRates.put(3000, new int[]{6000, 3000});
        relatedRates.put(5000, new int[]{5000, 2500});
        relatedRates.put(5994, new int[]{5994, 6000, 2997, 3000});
        relatedRates.put(6000, new int[]{6000, 3000});
        return relatedRates;
    }

    @Override
    public boolean restoreOriginalState(Window window) {
        Mode currentMode = getUhdHelper().getCurrentMode();

        if (currentMode != null && mOriginalMode != null && (currentMode.getPhysicalHeight() != mOriginalMode.getPhysicalHeight() || currentMode.getRefreshRate() < UI_LAGGING_THRESHOLD)) {
            String msg =
                    "Restoring original state: rate: " + mOriginalMode.getRefreshRate() +
                            ", resolution: " + mOriginalMode.getPhysicalWidth() + "x" + mOriginalMode.getPhysicalHeight();
            Log.d(TAG, msg);
            super.restoreOriginalState(window);
        }

        return false;
    }
}
