package com.liskovsoft.smartyoutubetv2.common.app.models.playback.handlers;

import android.os.Handler;
import android.os.Looper;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.views.VideoSettingsView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;

public class PlayerUiManager extends PlayerEventListenerHelper {
    private static final String TAG = PlayerUiManager.class.getSimpleName();
    private final Handler mHandler;
    private static final long UI_HIDE_TIMEOUT_MS = 2_000;
    private static final long SUGGESTIONS_RESET_TIMEOUT_MS = 500;

    public PlayerUiManager() {
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onKeyDown(int keyCode) {
        stopUiVisibilityTimer();
        stopSuggestionsPositionTimer();

        if (KeyHelpers.isBackKey(keyCode)) {
            startSuggestionsResetTimer();
        } else {
            startUiHideTimer();
        }
    }

    @Override
    public void onEngineReleased() {
        stopUiVisibilityTimer();
        stopSuggestionsPositionTimer();
    }

    @Override
    public void onRepeatModeClicked(int modeIndex) {
        mController.setRepeatMode(modeIndex);
    }

    @Override
    public void onRepeatModeChange(int modeIndex) {
        mController.setRepeatButtonState(modeIndex);
    }

    @Override
    public void onHighQualityClicked() {
        ViewManager.instance(mActivity).startView(VideoSettingsView.class);
    }

    @Override
    public void onSubscribeClicked(boolean subscribed) {
        // TODO: make network request
    }

    @Override
    public void onThumbsDownClicked(boolean thumbsDown) {
        // TODO: make network request
    }

    @Override
    public void onThumbsUpClicked(boolean thumbsUp) {
        // TODO: make network request
    }

    private void stopUiVisibilityTimer() {
        Log.d(TAG, "Stopping hide ui timer...");
        mHandler.removeCallbacks(mUiVisibilityHandler);
    }

    private void startUiHideTimer() {
        Log.d(TAG, "Starting hide ui timer...");
        mHandler.postDelayed(mUiVisibilityHandler, UI_HIDE_TIMEOUT_MS);
    }

    private void stopSuggestionsPositionTimer() {
        Log.d(TAG, "Stopping reset position timer...");
        mHandler.removeCallbacks(mSuggestionsPositionHandler);
    }

    private void startSuggestionsResetTimer() {
        Log.d(TAG, "Starting reset position timer...");
        mHandler.postDelayed(mSuggestionsPositionHandler, SUGGESTIONS_RESET_TIMEOUT_MS);
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
            startUiHideTimer();
        }
    };
}
