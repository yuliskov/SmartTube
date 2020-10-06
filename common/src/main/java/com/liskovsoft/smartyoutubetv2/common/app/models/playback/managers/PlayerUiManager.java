package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.SuggestionsLoader.MetadataListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;

import java.util.List;

public class PlayerUiManager extends PlayerEventListenerHelper implements MetadataListener {
    private static final String TAG = PlayerUiManager.class.getSimpleName();
    private final Handler mHandler;
    private static final long UI_AUTO_HIDE_TIMEOUT_MS = 2_000;
    private static final long UI_AUTO_SHOW_TIMEOUT_MS = 3_000;
    private static final long SUGGESTIONS_RESET_TIMEOUT_MS = 500;
    private boolean mEngineReady;
    private boolean mDebugViewEnabled;
    private final Runnable mSuggestionsResetHandler = () -> mController.resetSuggestedPosition();
    private final Runnable mUiAutoHideHandler = () -> {
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
    private final Runnable mUiAutoShowHandler = () -> {
        if (!mController.isPlaying()) {
            mController.showControls(true);
        }
    };

    public PlayerUiManager() {
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void openVideo(Video item) {
        // If video can't be loaded show at least some infos.
        enableUiAutoShowTimeout();
    }

    @Override
    public void onActivity(Activity activity) {
        super.onActivity(activity);

        AppSettingsPresenter.instance(activity).setPlayerUiManager(this);
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
    public void onChannelClicked() {
        ChannelPresenter.instance(mActivity).openChannel(mController.getVideo());
    }

    @Override
    public void onClosedCaptionsClicked() {
        List<FormatItem> subtitleFormats = mController.getSubtitleFormats();
        String subtitleFormatsTitle = mActivity.getString(R.string.subtitle_formats_title);

        AppSettingsPresenter settingsPresenter = AppSettingsPresenter.instance(mActivity);

        settingsPresenter.clear();

        settingsPresenter.appendRadioCategory(subtitleFormatsTitle,
                UiOptionItem.from(subtitleFormats,
                        option -> mController.selectFormat(UiOptionItem.toFormat(option)),
                        mActivity.getString(R.string.default_subtitle_option)));

        settingsPresenter.showDialog();
    }

    @Override
    public void onPlaylistAddClicked() {
        MessageHelpers.showMessage(mActivity, R.string.not_implemented);
    }

    @Override
    public void onVideoStatsClicked(boolean enabled) {
        mDebugViewEnabled = enabled;
        mController.showDebugView(enabled);
    }

    @Override
    public void onEngineInitialized() {
        mEngineReady = true;
    }

    @Override
    public void onVideoLoaded(Video item) {
        // Next lines on engine initialized stage cause other listeners to disappear.
        mController.showDebugView(mDebugViewEnabled);
        mController.setDebugButtonState(mDebugViewEnabled);
    }

    @Override
    public void onEngineReleased() {
        Log.d(TAG, "Engine released. Disabling all callbacks...");
        mEngineReady = false;

        disposeTimeouts();
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
    public void onMetadata(MediaItemMetadata metadata) {
        mController.setLikeButtonState(metadata.getLikeStatus() == MediaItemMetadata.LIKE_STATUS_LIKE);
        mController.setDislikeButtonState(metadata.getLikeStatus() == MediaItemMetadata.LIKE_STATUS_DISLIKE);
        mController.setSubscribeButtonState(metadata.isSubscribed());
    }

    @Override
    public void onSubscribeClicked(boolean subscribed) {
        Video video = mController.getVideo();

        if (video == null || video.mediaItem == null) {
            Log.e(TAG, "Seems that video isn't initialized yet. Cancelling...");
            return;
        }

        MediaService service = YouTubeMediaService.instance();
        MediaItemManager mediaItemManager = service.getMediaItemManager();

        Observable<Void> observable;

        if (subscribed) {
            observable = mediaItemManager.subscribeObserve(video.mediaItem);
        } else {
            observable = mediaItemManager.unsubscribeObserve(video.mediaItem);
        }

        observable
                .subscribeOn(Schedulers.newThread())
                .subscribe();
    }

    @Override
    public void onThumbsDownClicked(boolean thumbsDown) {
        Video video = mController.getVideo();

        if (video == null || video.mediaItem == null) {
            Log.e(TAG, "Seems that video isn't initialized yet. Cancelling...");
            return;
        }

        MediaService service = YouTubeMediaService.instance();
        MediaItemManager mediaItemManager = service.getMediaItemManager();

        Observable<Void> observable;

        if (thumbsDown) {
            observable = mediaItemManager.setDislikeObserve(video.mediaItem);
        } else {
            observable = mediaItemManager.removeDislikeObserve(video.mediaItem);
        }

        observable
                .subscribeOn(Schedulers.newThread())
                .subscribe();
    }

    @Override
    public void onThumbsUpClicked(boolean thumbsUp) {
        Video video = mController.getVideo();

        if (video == null || video.mediaItem == null) {
            Log.e(TAG, "Seems that video isn't initialized yet. Cancelling...");
            return;
        }

        MediaService service = YouTubeMediaService.instance();
        MediaItemManager mediaItemManager = service.getMediaItemManager();

        Observable<Void> observable;

        if (thumbsUp) {
            observable = mediaItemManager.setLikeObserve(video.mediaItem);
        } else {
            observable = mediaItemManager.removeLikeObserve(video.mediaItem);
        }

        observable
                .subscribeOn(Schedulers.newThread())
                .subscribe();
    }

    public void disableUiAutoHideTimeout() {
        Log.d(TAG, "Stopping auto hide ui timer...");
        mHandler.removeCallbacks(mUiAutoHideHandler);
    }

    public void enableUiAutoHideTimeout() {
        Log.d(TAG, "Starting auto hide ui timer...");
        if (mEngineReady) {
            mHandler.postDelayed(mUiAutoHideHandler, UI_AUTO_HIDE_TIMEOUT_MS);
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

    private void disableUiAutoShowTimeout() {
        Log.d(TAG, "Stopping auto show ui timer...");
        mHandler.removeCallbacks(mUiAutoShowHandler);
    }

    private void enableUiAutoShowTimeout() {
        Log.d(TAG, "Starting auto show ui timer...");
        if (mEngineReady) {
            mHandler.postDelayed(mUiAutoShowHandler, UI_AUTO_SHOW_TIMEOUT_MS);
        }
    }

    private void disposeTimeouts() {
        disableUiAutoShowTimeout();
        disableUiAutoHideTimeout();
        disableSuggestionsResetTimeout();
    }
}
