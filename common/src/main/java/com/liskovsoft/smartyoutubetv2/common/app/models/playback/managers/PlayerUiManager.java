package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import android.app.Activity;
import android.os.Build.VERSION;
import android.os.Handler;
import android.os.Looper;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.VideoSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PlayerUiManager extends PlayerEventListenerHelper {
    private static final String TAG = PlayerUiManager.class.getSimpleName();
    private final Handler mHandler;
    private static final long UI_HIDE_TIMEOUT_MS = 2_000;
    private static final long SUGGESTIONS_RESET_TIMEOUT_MS = 500;
    private boolean mEngineReady;
    private VideoSettingsPresenter mSettingsPresenter;
    // NOTE: using map, because same item could be changed time to time
    private final Map<String, List<OptionItem>> mCheckedCategories = new LinkedHashMap<>();
    private final Map<String, List<OptionItem>> mRadioCategories = new LinkedHashMap<>();
    private final Map<CharSequence, OptionItem> mSingleOptions = new LinkedHashMap<>();
    private boolean mBlockEngine;
    private boolean mEnablePIP;

    public PlayerUiManager() {
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onActivity(Activity activity) {
        super.onActivity(activity);

        mSettingsPresenter = VideoSettingsPresenter.instance(mActivity);
    }

    private void addQualityCategories() {
        List<FormatItem> videoFormats = mController.getVideoFormats();
        String videoFormatsTitle = mActivity.getString(R.string.dialog_video_formats);

        List<FormatItem> audioFormats = mController.getAudioFormats();
        String audioFormatsTitle = mActivity.getString(R.string.dialog_audio_formats);

        addRadioCategory(videoFormatsTitle,
                UiOptionItem.from(videoFormats,
                        option -> mController.selectFormat(UiOptionItem.toFormat(option)),
                        mActivity.getString(R.string.dialog_video_default)));
        addRadioCategory(audioFormatsTitle,
                UiOptionItem.from(audioFormats,
                        option -> mController.selectFormat(UiOptionItem.toFormat(option)),
                        mActivity.getString(R.string.dialog_audio_default)));
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
        addQualityCategories();
        addBackgroundPlaybackCategory();

        disableUiAutoHideTimeout();

        if (VERSION.SDK_INT < 25) {
            // Old Android fix: don't destroy player while dialog is open
            mController.blockEngine(true);
        }

        mSettingsPresenter.clear();

        createRadioOptions();
        createCheckedOptions();
        createSingleOptions();

        mSettingsPresenter.showDialog(mActivity.getString(R.string.playback_settings),
                () -> {
                    enableUiAutoHideTimeout();
                    updateBackgroundPlayback();
                });
    }

    @Override
    public void onSubscribeClicked(boolean subscribed) {
        if (mController.getVideo() == null) {
            Log.e(TAG, "Seems that video isn't initialized yet. Cancelling...");
            return;
        }

        MediaService service = YouTubeMediaService.instance();
        MediaItemManager mediaItemManager = service.getMediaItemManager();

        Observable<Void> observable;

        if (subscribed) {
            observable = mediaItemManager.subscribeObserve(mController.getVideo().mediaItem);
        } else {
            observable = mediaItemManager.unsubscribeObserve(mController.getVideo().mediaItem);
        }

        observable
                .subscribeOn(Schedulers.newThread())
                .subscribe();
    }

    @Override
    public void onThumbsDownClicked(boolean thumbsDown) {
        if (mController.getVideo() == null) {
            Log.e(TAG, "Seems that video isn't initialized yet. Cancelling...");
            return;
        }

        MediaService service = YouTubeMediaService.instance();
        MediaItemManager mediaItemManager = service.getMediaItemManager();

        Observable<Void> observable;

        if (thumbsDown) {
            observable = mediaItemManager.setDislikeObserve(mController.getVideo().mediaItem);
        } else {
            observable = mediaItemManager.removeDislikeObserve(mController.getVideo().mediaItem);
        }

        observable
                .subscribeOn(Schedulers.newThread())
                .subscribe();
    }

    @Override
    public void onThumbsUpClicked(boolean thumbsUp) {
        if (mController.getVideo() == null) {
            Log.e(TAG, "Seems that video isn't initialized yet. Cancelling...");
            return;
        }

        MediaService service = YouTubeMediaService.instance();
        MediaItemManager mediaItemManager = service.getMediaItemManager();

        Observable<Void> observable;

        if (thumbsUp) {
            observable = mediaItemManager.setLikeObserve(mController.getVideo().mediaItem);
        } else {
            observable = mediaItemManager.removeLikeObserve(mController.getVideo().mediaItem);
        }

        observable
                .subscribeOn(Schedulers.newThread())
                .subscribe();
    }

    @Override
    public void onChannelClicked() {
        MessageHelpers.showMessage(mActivity, R.string.not_implemented);
    }

    @Override
    public void onClosedCaptionsClicked() {
        MessageHelpers.showMessage(mActivity, R.string.not_implemented);
    }

    @Override
    public void onPlaylistAddClicked() {
        MessageHelpers.showMessage(mActivity, R.string.not_implemented);
    }

    @Override
    public void onVideoStatsClicked() {
        MessageHelpers.showMessage(mActivity, R.string.not_implemented);
    }

    private void updateBackgroundPlayback() {
        if (mBlockEngine) {
            // return to the player regardless the last activity user watched in moment exiting to HOME
            ViewManager.instance(mActivity).blockTop(mActivity);
        } else {
            ViewManager.instance(mActivity).blockTop(null);
        }

        mController.blockEngine(mBlockEngine);
        mController.enablePIP(mEnablePIP);
    }

    private void addBackgroundPlaybackCategory() {
        String categoryTitle = mActivity.getString(R.string.category_background_playback);

        List<OptionItem> options = new ArrayList<>();
        options.add(UiOptionItem.from(mActivity.getString(R.string.option_background_playback_off),
                optionItem -> {
                    mBlockEngine = false;
                    mEnablePIP = false;
                    updateBackgroundPlayback();
                }, !mBlockEngine && !mEnablePIP));
        options.add(UiOptionItem.from(mActivity.getString(R.string.option_background_playback_all),
                optionItem -> {
                    mBlockEngine = true;
                    mEnablePIP = true;
                    updateBackgroundPlayback();
                }, mEnablePIP && mBlockEngine));
        options.add(UiOptionItem.from(mActivity.getString(R.string.option_background_playback_only_audio),
                optionItem -> {
                    mBlockEngine = true;
                    mEnablePIP = false;
                    updateBackgroundPlayback();
                }, mBlockEngine && !mEnablePIP));

        addRadioCategory(categoryTitle, options);
    }

    public void addSingleOption(OptionItem option) {
        mSingleOptions.put(option.getTitle(), option);
    }

    public void addCheckedCategory(String categoryTitle, List<OptionItem> options) {
        mCheckedCategories.put(categoryTitle, options);
    }

    public void addRadioCategory(String categoryTitle, List<OptionItem> options) {
        mRadioCategories.put(categoryTitle, options);
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

    private void createSingleOptions() {
        for (OptionItem option : mSingleOptions.values()) {
            mSettingsPresenter.appendSingleSwitch(option);
        }
    }

    private void createCheckedOptions() {
        for (String key : mCheckedCategories.keySet()) {
            mSettingsPresenter.appendChecked(key, mCheckedCategories.get(key));
        }
    }

    private void createRadioOptions() {
        for (String key : mRadioCategories.keySet()) {
            mSettingsPresenter.appendRadio(key, mRadioCategories.get(key));
        }
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
