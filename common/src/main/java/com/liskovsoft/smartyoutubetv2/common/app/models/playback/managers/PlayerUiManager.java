package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import android.app.Activity;
import android.os.Build.VERSION;
import android.os.Handler;
import android.os.Looper;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.VideoSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerUiManager extends PlayerEventListenerHelper {
    private static final String TAG = PlayerUiManager.class.getSimpleName();
    private final Handler mHandler;
    private static final long UI_HIDE_TIMEOUT_MS = 2_000;
    private static final long SUGGESTIONS_RESET_TIMEOUT_MS = 500;
    private boolean mEngineReady;
    private VideoSettingsPresenter mSettingsPresenter;
    private final Map<String, List<OptionItem>> mSwitches = new HashMap<>();
    private OptionItem mBackgroundPlaybackSwitch;
    private boolean mRunOnce;
    private boolean mBlockEngine;

    public PlayerUiManager() {
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onMainActivity(Activity activity) {
        super.onMainActivity(activity);

        mSettingsPresenter = VideoSettingsPresenter.instance(mMainActivity);

        if (!mRunOnce) {
            setupBackgroundPlayback();
            mRunOnce = true;
        }
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
    public void onEngineInitialized() {
        mEngineReady = true;

        updateBackgroundPlayback();
    }

    @Override
    public void onEngineReleased() {
        Log.d(TAG, "Engine released. Disabling all callbacks...");
        mEngineReady = false;

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

        if (VERSION.SDK_INT < 25) {
            // Old Android fix: don't destroy player while dialog is open
            mController.blockEngine();
        }

        mSettingsPresenter.clear();

        addRadioList();
        addCheckedList();
        addSingleOption();

        mSettingsPresenter.showDialog(() -> {
            enableUiAutoHideTimeout();

            updateBackgroundPlayback();
        });
    }

    @Override
    public void onSubscribeClicked(boolean subscribed) {
        MessageHelpers.showMessage(mMainActivity, R.string.not_implemented);
    }

    @Override
    public void onThumbsDownClicked(boolean thumbsDown) {
        MessageHelpers.showMessage(mMainActivity, R.string.not_implemented);
    }

    @Override
    public void onThumbsUpClicked(boolean thumbsUp) {
        MessageHelpers.showMessage(mMainActivity, R.string.not_implemented);
    }

    @Override
    public void onChannelClicked() {
        MessageHelpers.showMessage(mMainActivity, R.string.not_implemented);
    }

    @Override
    public void onClosedCaptionsClicked() {
        MessageHelpers.showMessage(mMainActivity, R.string.not_implemented);
    }

    @Override
    public void onPlaylistAddClicked() {
        MessageHelpers.showMessage(mMainActivity, R.string.not_implemented);
    }

    @Override
    public void onVideoStatsClicked() {
        MessageHelpers.showMessage(mMainActivity, R.string.not_implemented);
    }

    private void setupBackgroundPlayback() {
        mBackgroundPlaybackSwitch = UiOptionItem.from(
                mMainActivity.getString(R.string.dialog_background_playback),
                optionItem -> {
                    mBlockEngine = optionItem.isSelected();
                    updateBackgroundPlayback();
                }, mBlockEngine);
    }

    private void updateBackgroundPlayback() {
        if (mBlockEngine) {
            mController.blockEngine();
            ViewManager.instance(mMainActivity).blockTop(true); // open player regarding its position in stack
        } else {
            mController.unblockEngine();
            ViewManager.instance(mMainActivity).blockTop(false);
        }
    }

    public void addHQSwitch(String categoryTitle, OptionItem optionItem) {
        List<OptionItem> items = mSwitches.get(categoryTitle);

        if (items == null) {
            items = new ArrayList<>();
            mSwitches.put(categoryTitle, items);
        }

        items.add(optionItem);
    }

    private void disableUiAutoHideTimeout() {
        Log.d(TAG, "Stopping hide ui timer...");
        mHandler.removeCallbacks(mUiVisibilityHandler);
    }

    private void enableUiAutoHideTimeout() {
        Log.d(TAG, "Starting hide ui timer...");
        if (mEngineReady) {
            mHandler.postDelayed(mUiVisibilityHandler, UI_HIDE_TIMEOUT_MS);
        }
    }

    private void disableSuggestionsResetTimeout() {
        Log.d(TAG, "Stopping reset position timer...");
        mHandler.removeCallbacks(mSuggestionsResetHandler);
    }

    private void enableSuggestionsResetTimeout() {
        Log.d(TAG, "Starting reset position timer...");
        if (mEngineReady) {
            mHandler.postDelayed(mSuggestionsResetHandler, SUGGESTIONS_RESET_TIMEOUT_MS);
        }
    }

    private void addSingleOption() {
        mSettingsPresenter.appendSingleSwitch(mBackgroundPlaybackSwitch);
    }

    private void addCheckedList() {
        for (String key : mSwitches.keySet()) {
            mSettingsPresenter.appendChecked(key, mSwitches.get(key));
        }
    }

    private void addRadioList() {
        List<FormatItem> videoFormats = mController.getVideoFormats();
        String videoFormatsTitle = mMainActivity.getString(R.string.dialog_video_formats);

        List<FormatItem> audioFormats = mController.getAudioFormats();
        String audioFormatsTitle = mMainActivity.getString(R.string.dialog_audio_formats);

        mSettingsPresenter.appendRadio(videoFormatsTitle,
                UiOptionItem.from(videoFormats,
                        option -> mController.selectFormat(UiOptionItem.toFormat(option)),
                        mMainActivity.getString(R.string.dialog_video_default)));
        mSettingsPresenter.appendRadio(audioFormatsTitle,
                UiOptionItem.from(audioFormats,
                        option -> mController.selectFormat(UiOptionItem.toFormat(option)),
                        mMainActivity.getString(R.string.dialog_audio_default)));
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
