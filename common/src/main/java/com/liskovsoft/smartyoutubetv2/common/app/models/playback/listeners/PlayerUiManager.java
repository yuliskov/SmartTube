package com.liskovsoft.smartyoutubetv2.common.app.models.playback.listeners;

import android.os.Handler;
import android.os.Looper;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;

public class PlayerUiManager extends PlayerEventListenerHelper {
    private static final String TAG = PlayerUiManager.class.getSimpleName();
    private final Handler mHandler;
    private static final long UI_HIDE_TIMEOUT_MS = 2_000;

    public PlayerUiManager() {
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onKeyDown(int keyCode) {
        startTimer();
    }

    private void startTimer() {
        Log.d(TAG, "Starting hide ui timer...");
        mHandler.removeCallbacks(mHideUiHandler);
        mHandler.postDelayed(mHideUiHandler, UI_HIDE_TIMEOUT_MS);
    }

    private final Runnable mHideUiHandler = () -> {
        if (mController.isPlaying()) {
            mController.showControls(false);
        } else {
            // in seeking state? doing recheck...
            startTimer();
        }
    };
}
