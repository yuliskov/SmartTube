package com.liskovsoft.smartyoutubetv2.common.app.models.playback.handlers;

import android.os.Handler;
import android.os.Looper;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.VideoSettingsPresenter;

import java.util.List;

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
        disableUiAutoHideTimeout();
        disableSuggestionsResetTimeout();

        if (KeyHelpers.isBackKey(keyCode)) {
            enableSuggestionsResetTimeout();
        } else {
            enableUiAutoHideTimeout();
        }
    }

    @Override
    public void onEngineReleased() {
        disableUiAutoHideTimeout();
        disableSuggestionsResetTimeout();
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
        disableUiAutoHideTimeout();
        mController.blockEngine();

        List<OptionItem> videoFormats = mController.getVideoFormats();
        String videoFormatsTitle = mActivity.getString(R.string.dialog_video_formats);

        List<OptionItem> audioFormats = mController.getAudioFormats();
        String audioFormatsTitle = mActivity.getString(R.string.dialog_audio_formats);

        VideoSettingsPresenter settingsPresenter = VideoSettingsPresenter.instance(mActivity);
        settingsPresenter.clear();
        settingsPresenter.append(videoFormatsTitle, videoFormats, option -> mController.selectFormat(option));
        settingsPresenter.append(audioFormatsTitle, audioFormats, option -> mController.selectFormat(option));

        settingsPresenter.showDialog(() -> {
            enableUiAutoHideTimeout();
            mController.unblockEngine();
        });
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

    @Override
    public void onChannelClicked() {
        // TODO: open channel view
    }

    private void disableUiAutoHideTimeout() {
        Log.d(TAG, "Stopping hide ui timer...");
        mHandler.removeCallbacks(mUiVisibilityHandler);
    }

    private void enableUiAutoHideTimeout() {
        Log.d(TAG, "Starting hide ui timer...");
        mHandler.postDelayed(mUiVisibilityHandler, UI_HIDE_TIMEOUT_MS);
    }

    private void disableSuggestionsResetTimeout() {
        Log.d(TAG, "Stopping reset position timer...");
        mHandler.removeCallbacks(mSuggestionsResetHandler);
    }

    private void enableSuggestionsResetTimeout() {
        Log.d(TAG, "Starting reset position timer...");
        mHandler.postDelayed(mSuggestionsResetHandler, SUGGESTIONS_RESET_TIMEOUT_MS);
    }

    private final Runnable mSuggestionsResetHandler = () -> mController.resetSuggestedPosition();

    private final Runnable mUiVisibilityHandler = () -> {
        if (mController.isPlaying()) {
            if (!mController.isSuggestionsShown()) { // don't hide when suggestions is shown
                mController.showControls(false);
            }
        } else {
            // in seeking state? doing recheck...
            disableUiAutoHideTimeout();
            enableUiAutoHideTimeout();
        }
    };
}
