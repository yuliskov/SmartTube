package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import android.os.Handler;
import android.os.Looper;
import androidx.core.util.Pair;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.VideoSettingsPresenter;
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

    public interface SwitchCallback {
        void onClick(boolean checked);
    }

    public PlayerUiManager() {
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void setController(PlayerController controller) {
        super.setController(controller);
        mSettingsPresenter = VideoSettingsPresenter.instance(mActivity);
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
        mController.blockEngine();

        mSettingsPresenter.clear();

        addListOption();
        addSwitches();

        mSettingsPresenter.showDialog(() -> {
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

    private void addSwitches() {
        //for (Pair<String, SwitchCallback> pair : mSwitches) {
        //    mSettingsPresenter.appendChecked(pair.first, pair.second);
        //}
    }

    private void addListOption() {
        List<FormatItem> videoFormats = mController.getVideoFormats();
        String videoFormatsTitle = mActivity.getString(R.string.dialog_video_formats);

        List<FormatItem> audioFormats = mController.getAudioFormats();
        String audioFormatsTitle = mActivity.getString(R.string.dialog_audio_formats);

        mSettingsPresenter.appendChecked(videoFormatsTitle,
                UiOptionItem.from(videoFormats,
                        option -> mController.selectFormat(UiOptionItem.toFormat(option)),
                        mActivity.getString(R.string.dialog_video_default)));
        mSettingsPresenter.appendChecked(audioFormatsTitle,
                UiOptionItem.from(audioFormats,
                        option -> mController.selectFormat(UiOptionItem.toFormat(option)),
                        mActivity.getString(R.string.dialog_audio_default)));
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
