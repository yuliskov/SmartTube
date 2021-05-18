package com.liskovsoft.smartyoutubetv2.common.autoframerate;

import android.app.Activity;
import android.util.Pair;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.internal.DisplayHolder.Mode;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.internal.DisplaySyncHelper;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.internal.DisplaySyncHelper.AutoFrameRateListener;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.internal.DisplaySyncHelperAlt;

import java.util.HashMap;

public class AutoFrameRateHelper {
    private static final String TAG = AutoFrameRateHelper.class.getSimpleName();
    private final DisplaySyncHelper mSyncHelper;
    private static final long THROTTLE_INTERVAL_MS = 5_000;
    private long mPrevCall;
    private HashMap<Float, Float> mFrameRateMapping;
    private boolean mIsFpsCorrectionEnabled;

    public AutoFrameRateHelper() {
        mSyncHelper = new DisplaySyncHelperAlt(null);

        initFrameRateMapping();
    }

    public void apply(Activity activity, FormatItem format) {
        apply(activity, format, false);
    }

    public boolean isSupported() {
        resetStats();
        return mSyncHelper.supportsDisplayModeChangeComplex();
    }

    public boolean isResolutionSwitchEnabled() {
        return mSyncHelper.isResolutionSwitchEnabled();
    }

    public void setResolutionSwitchEnabled(boolean enabled, boolean force) {
        if (force) {
            Mode originalMode = mSyncHelper.getOriginalMode();
            Mode newMode = mSyncHelper.getNewMode();

            if (originalMode != null && newMode != null) {
                if (enabled) {
                    mSyncHelper.setResolutionSwitchEnabled(true);
                    //syncMode(mCurrentFormat.first, mCurrentFormat.second);
                } else {
                    //syncMode(originalMode.getPhysicalWidth(), newMode.getRefreshRate());
                    mSyncHelper.setResolutionSwitchEnabled(false);
                }
            }
        } else {
            mSyncHelper.setResolutionSwitchEnabled(enabled);
        }
    }

    public void saveOriginalState(Activity activity) {
        setActivity(activity);

        if (activity == null) {
            Log.e(TAG, "Activity in null. exiting...");
            return;
        }

        if (!isSupported()) {
            return;
        }

        mSyncHelper.saveOriginalState();
    }

    private void initFrameRateMapping() {
        mFrameRateMapping = new HashMap<>();
        mFrameRateMapping.put(24f, 23.97f);
        mFrameRateMapping.put(30f, 29.97f);
        mFrameRateMapping.put(60f, 59.94f);
    }

    public void apply(Activity activity, FormatItem format, boolean force) {
        setActivity(activity);

        if (activity == null) {
            Log.e(TAG, "Activity in null. exiting...");
            return;
        }

        if (!isSupported()) {
            Log.e(TAG, "Autoframerate not supported. Exiting...");
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

        int width = format.getWidth();
        float frameRate = correctFrameRate(format.getFrameRate());

        Pair<Integer, Float> currentFormat = new Pair<>(width, frameRate);

        Log.d(TAG, String.format("Applying mode change... Video fps: %s, width: %s, height: %s", frameRate, width, format.getHeight()));

        syncMode(activity, width, frameRate, force);
    }

    //private void syncMode(int width, float frameRate) {
    //    syncMode(width, frameRate, false);
    //}

    private void syncMode(Activity activity, int width, float frameRate, boolean force) {
        if (activity == null) {
            Log.e(TAG, "Activity in null. exiting...");
            return;
        }

        if (!isSupported()) {
            Log.e(TAG, "Autoframerate not supported. Exiting...");
            return;
        }

        mSyncHelper.syncDisplayMode(activity.getWindow(), width, frameRate, force);
    }

    public void restoreOriginalState(Activity activity) {
        restoreOriginalState(activity, false);
    }

    private void restoreOriginalState(Activity activity, boolean force) {
        if (!isSupported()) {
            Log.d(TAG, "restoreOriginalState: autoframerate not enabled... exiting...");
            return;
        }

        Log.d(TAG, "Restoring original mode...");

        boolean result = mSyncHelper.restoreOriginalState(activity.getWindow(), force);

        Log.d(TAG, "Restore mode result: " + result);
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

    private float correctFrameRate(float frameRate) {
        if (mIsFpsCorrectionEnabled && mFrameRateMapping.containsKey(frameRate)) {
            return mFrameRateMapping.get(frameRate);
        }

        return frameRate;
    }

    ///**
    // * UGOOS mode change fix. DEPRECATED!
    // */
    //private void applyModeChangeFix() {
    //    if (!isSupported()) {
    //        return;
    //    }
    //
    //    mSyncHelper.applyModeChangeFix(mActivity.getWindow());
    //}

    //private void resetState() {
    //    mSyncHelper.resetMode(mActivity.getWindow());
    //}

    private void setActivity(Activity activity) {
        mSyncHelper.setContext(activity);
    }
}
