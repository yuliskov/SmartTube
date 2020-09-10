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
        stopUiVisibilityTimer();
        stopSuggestionsPositionTimer();

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            startSuggestionsPositionTimer();
        } else {
            startUiVisibilityTimer();
        }
    }

    @Override
    public void onEngineReleased() {
        stopUiVisibilityTimer();
        stopSuggestionsPositionTimer();
    }

    private void stopUiVisibilityTimer() {
        Log.d(TAG, "Stopping hide ui timer...");
        mHandler.removeCallbacks(mUiVisibilityHandler);
    }

    private void startUiVisibilityTimer() {
        Log.d(TAG, "Starting hide ui timer...");
        mHandler.postDelayed(mUiVisibilityHandler, UI_HIDE_TIMEOUT_MS);
    }

    private void stopSuggestionsPositionTimer() {
        Log.d(TAG, "Stopping reset position timer...");
        mHandler.removeCallbacks(mSuggestionsPositionHandler);
    }

    private void startSuggestionsPositionTimer() {
        Log.d(TAG, "Starting reset position timer...");
        mHandler.postDelayed(mSuggestionsPositionHandler, RESET_TIMEOUT_MS);
    }

    private final Runnable mSuggestionsPositionHandler = () -> mController.resetSuggestedPosition();

    private final Runnable mUiVisibilityHandler = () -> {
        if (mController.isPlaying()) {
            if (!mController.isSuggestionsShown()) { // don't hide when suggestions is shown
                mController.showControls(false);
            }
        } else {
            // in seeking state? doing recheck...
            stopUiVisibilityTimer();
            startUiVisibilityTimer();
        }
    };
}
