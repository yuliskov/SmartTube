package com.liskovsoft.smartyoutubetv2.common.autoframerate;

import android.app.Activity;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.internal.DisplaySyncHelper;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.internal.DisplaySyncHelper.AutoFrameRateListener;

import java.util.HashMap;

public class AutoFrameRateHelper {
    private static final String TAG = AutoFrameRateHelper.class.getSimpleName();
    private final Activity mActivity;
    private final DisplaySyncHelper mSyncHelper;
    private static final long THROTTLE_INTERVAL_MS = 5_000;
    private long mPrevCall;
    private HashMap<Float, Float> mFrameRateMapping;
    private boolean mIsFpsCorrectionEnabled;

    public AutoFrameRateHelper(Activity activity) {
        mActivity = activity;
        mSyncHelper = new DisplaySyncHelper(activity);

        initFrameRateMapping();
        saveOriginalState();
    }

    private void initFrameRateMapping() {
        mFrameRateMapping = new HashMap<>();
        mFrameRateMapping.put(30f, 29.97f);
        mFrameRateMapping.put(60f, 59.94f);
    }

    public void apply(FormatItem format) {
        if (!isSupported()) {
            Log.d(TAG, "Autoframerate not supported... exiting...");
            return;
        }

        if (format == null) {
            Log.e(TAG, "Can't apply mode change: format is null");
            return;
        }

        if (System.currentTimeMillis() - mPrevCall < THROTTLE_INTERVAL_MS) {
            Log.e(TAG, "Throttling afr calls...");
            return;
        } else {
            mPrevCall = System.currentTimeMillis();
        }
        
        float frameRate = correctFps(format.getFrameRate());
        int width = format.getWidth();

        Log.d(TAG, String.format("Applying mode change... Video fps: %s, width: %s", frameRate, width));
        mSyncHelper.syncDisplayMode(mActivity.getWindow(), width, frameRate);
    }

    public boolean isSupported() {
        return mSyncHelper.supportsDisplayModeChangeComplex();
    }

    public boolean isResolutionSwitchEnabled() {
        return mSyncHelper.isResolutionSwitchEnabled();
    }

    public void setResolutionSwitchEnabled(boolean enabled) {
        mSyncHelper.setResolutionSwitchEnabled(enabled);
    }

    private void saveOriginalState() {
        if (!isSupported()) {
            return;
        }

        mSyncHelper.saveOriginalState();
    }

    public void restoreOriginalState() {
        if (!isSupported()) {
            Log.d(TAG, "restoreOriginalState: autoframerate not enabled... exiting...");
            return;
        }

        Log.d(TAG, "Restoring original mode...");

        mSyncHelper.restoreOriginalState(mActivity.getWindow());
    }

    public void setListener(AutoFrameRateListener listener) {
        mSyncHelper.setListener(listener);
    }

    public boolean isFpsCorrectionEnabled() {
        return mIsFpsCorrectionEnabled;
    }

    public void setFpsCorrectionEnabled(boolean enabled) {
        mIsFpsCorrectionEnabled = enabled;
    }

    private void resetStats() {
        mSyncHelper.resetStats();
    }

    private float correctFps(float frameRate) {
        if (mIsFpsCorrectionEnabled && mFrameRateMapping.containsKey(frameRate)) {
            return mFrameRateMapping.get(frameRate);
        }

        return frameRate;
    }

    /**
     * UGOOS mode change fix. DEPRECATED!
     */
    private void applyModeChangeFix() {
        if (!isSupported()) {
            return;
        }

        mSyncHelper.applyModeChangeFix(mActivity.getWindow());
    }
}
