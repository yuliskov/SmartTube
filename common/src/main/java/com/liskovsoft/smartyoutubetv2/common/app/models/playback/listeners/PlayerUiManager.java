package com.liskovsoft.smartyoutubetv2.common.app.models.playback.listeners;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;

public class PlayerUiManager extends PlayerEventListenerHelper {
    private static final String TAG = PlayerUiManager.class.getSimpleName();
    private final Handler mHandler;
    private static final long UI_HIDE_TIMEOUT_MS = 2_000;
    private static final long RESET_TIMEOUT_MS = 1_000;

    public PlayerUiManager() {
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onKeyDown(int keyCode) {
        stopHideUiTimer();

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            startResetPositionTimer();
        } else {
            startHideUiTimer();
        }
    }

    @Override
    public void onEngineReleased() {
        stopHideUiTimer();
    }

    private void stopHideUiTimer() {
        Log.d(TAG, "Stopping hide ui timer...");
        mHandler.removeCallbacks(mHideUiHandler);
    }

    private void startHideUiTimer() {
        Log.d(TAG, "Starting hide ui timer...");
        mHandler.postDelayed(mHideUiHandler, UI_HIDE_TIMEOUT_MS);
    }

    private void stopResetPositionTimer() {
        Log.d(TAG, "Stopping reset position timer...");
        mHandler.postDelayed(mResetPositionHandler, RESET_TIMEOUT_MS);
    }

    private void startResetPositionTimer() {
        Log.d(TAG, "Starting reset position timer...");
        mHandler.postDelayed(mResetPositionHandler, RESET_TIMEOUT_MS);
    }

    private final Runnable mResetPositionHandler = () -> mController.resetSuggestedPosition();

    private final Runnable mHideUiHandler = () -> {
        if (mController.isPlaying()) {
            if (!mController.isSuggestionsShown()) { // don't hide when suggestions is shown
                mController.showControls(false);
            }
        } else {
            // in seeking state? doing recheck...
            stopHideUiTimer();
            startHideUiTimer();
        }
    };
}
