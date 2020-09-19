package com.liskovsoft.smartyoutubetv2.common.autoframerate;

import android.app.Activity;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.internal.DisplaySyncHelper;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.internal.DisplaySyncHelper.AutoFrameRateListener;

import java.util.HashMap;

class AutoFrameRateHelper {
    private static final String TAG = AutoFrameRateHelper.class.getSimpleName();
    private final Activity mContext;
    private final DisplaySyncHelper mSyncHelper;
    private SimpleExoPlayer mPlayer;
    private static final long THROTTLE_INTERVAL_MS = 5_000;
    private long mPrevCall;
    private HashMap<Float, Float> mFrameRateMapping;
    private boolean mIsAfr60fpsCorrectionEnabled;

    public AutoFrameRateHelper(Activity context, DisplaySyncHelper syncHelper, boolean enableResolutionSwitch) {
        mContext = context;
        mSyncHelper = syncHelper;

        mSyncHelper.setResolutionSwitchEnabled(enableResolutionSwitch);

        initFrameRateMapping();
    }

    private void initFrameRateMapping() {
        mFrameRateMapping = new HashMap<>();
        mFrameRateMapping.put(30f, 29.97f);
        mFrameRateMapping.put(60f, 59.94f);
    }

    public void apply() {
        if (!getEnabled()) {
            Log.d(TAG, "Autoframerate not enabled... exiting...");
            return;
        }

        if (mPlayer == null) {
            Log.e(TAG, "Can't apply mode change: player is null");
            return;
        }

        if (mPlayer.getVideoFormat() == null) {
            Log.e(TAG, "Can't apply mode change: format is null");
            return;
        }

        if (System.currentTimeMillis() - mPrevCall < THROTTLE_INTERVAL_MS) {
            Log.e(TAG, "Throttling afr calls...");
            return;
        } else {
            mPrevCall = System.currentTimeMillis();
        }

        Format videoFormat = mPlayer.getVideoFormat();
        float frameRate = correctFps(videoFormat.frameRate);

        int width = videoFormat.width;
        Log.d(TAG, String.format("Applying mode change... Video fps: %s, width: %s", frameRate, width));
        mSyncHelper.syncDisplayMode(mContext.getWindow(), width, frameRate);
    }

    public boolean getEnabled() {
        return mSyncHelper.supportsDisplayModeChangeComplex();
    }

    public void setEnabled(boolean enabled) {
        if (!mSyncHelper.supportsDisplayModeChangeComplex()) {
            Log.e(TAG, "Autoframerate isn't supported");
            //MessageHelpers.showMessage(mContext, R.string.autoframerate_not_supported);
            enabled = false;
        }
        
        apply();
    }

    public boolean isResolutionSwitchEnabled() {
        return mSyncHelper.isAfrResolutionSwitchEnabled();
    }

    public void setResolutionSwitchEnabled(boolean enabled) {
        mSyncHelper.setResolutionSwitchEnabled(enabled);
    }

    public void applyModeChangeFix() {
        if (!getEnabled()) {
            return;
        }

        mSyncHelper.applyModeChangeFix(mContext.getWindow());
    }

    public void saveOriginalState() {
        if (!getEnabled()) {
            return;
        }

        mSyncHelper.saveOriginalState();
    }

    public void restoreOriginalState() {
        if (!getEnabled()) {
            Log.d(TAG, "restoreOriginalState: autoframerate not enabled... exiting...");
            return;
        }

        Log.d(TAG, "Restoring original mode...");

        mSyncHelper.restoreOriginalState(mContext.getWindow());
    }

    public void setPlayer(SimpleExoPlayer player) {
        mPlayer = player;
    }

    public void setListener(AutoFrameRateListener listener) {
        mSyncHelper.setListener(listener);
    }

    public void resetStats() {
        mSyncHelper.resetStats();
    }

    private float correctFps(float frameRate) {
        if (mIsAfr60fpsCorrectionEnabled && mFrameRateMapping.containsKey(frameRate)) {
            return mFrameRateMapping.get(frameRate);
        }

        return frameRate;
    }

    public boolean is60fpsCorrectionEnabled() {
        return mIsAfr60fpsCorrectionEnabled;
    }

    public void set60fpsCorrectionEnabled(boolean enabled) {
        mIsAfr60fpsCorrectionEnabled = enabled;
    }
}
