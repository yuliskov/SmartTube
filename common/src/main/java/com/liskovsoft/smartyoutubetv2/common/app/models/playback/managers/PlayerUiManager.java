package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import android.os.Handler;
import android.os.Looper;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.SuggestionsLoader.MetadataListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.VideoMenuPresenter;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.List;

public class PlayerUiManager extends PlayerEventListenerHelper implements MetadataListener {
    private static final String TAG = PlayerUiManager.class.getSimpleName();
    private static final long UI_AUTO_HIDE_TIMEOUT_MS = 3_000;
    private static final long SUGGESTIONS_RESET_TIMEOUT_MS = 500;
    private final Handler mHandler;
    private final MediaItemManager mMediaItemManager;
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

    public PlayerUiManager() {
        mHandler = new Handler(Looper.getMainLooper());

        MediaService service = YouTubeMediaService.instance();
        mMediaItemManager = service.getMediaItemManager();
    }

    @Override
    public void onInitDone() {
        AppSettingsPresenter.instance(mActivity).setPlayerUiManager(this);
    }

    @Override
    public void onKeyDown(int keyCode) {
        disableUiAutoHideTimeout();
        disableSuggestionsResetTimeout();

        if (KeyHelpers.isBackKey(keyCode)) {
            enableSuggestionsResetTimeout();
        }

        enableUiAutoHideTimeout();
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
        VideoMenuPresenter mp = VideoMenuPresenter.instance(mActivity);

        mp.showMenu(mController.getVideo());
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

    //@Override
    //public void onRepeatModeClicked(int modeIndex) {
    //    //mController.setRepeatMode(modeIndex);
    //}

    @Override
    public void onMetadata(MediaItemMetadata metadata) {
        mController.setLikeButtonState(metadata.getLikeStatus() == MediaItemMetadata.LIKE_STATUS_LIKE);
        mController.setDislikeButtonState(metadata.getLikeStatus() == MediaItemMetadata.LIKE_STATUS_DISLIKE);
        mController.setSubscribeButtonState(metadata.isSubscribed());
    }

    @Override
    public void onSuggestionItemLongClicked(Video item) {
        VideoMenuPresenter.instance(mActivity).showMenu(item);
    }

    @Override
    public void onSubscribeClicked(boolean subscribed) {
        if (subscribed) {
            callMediaItemObservable(mMediaItemManager::subscribeObserve);
        } else {
            callMediaItemObservable(mMediaItemManager::unsubscribeObserve);
        }
    }

    @Override
    public void onThumbsDownClicked(boolean thumbsDown) {
        if (thumbsDown) {
            callMediaItemObservable(mMediaItemManager::setDislikeObserve);
        } else {
            callMediaItemObservable(mMediaItemManager::removeDislikeObserve);
        }
    }

    @Override
    public void onThumbsUpClicked(boolean thumbsUp) {
        if (thumbsUp) {
            callMediaItemObservable(mMediaItemManager::setLikeObserve);
        } else {
            callMediaItemObservable(mMediaItemManager::removeLikeObserve);
        }
    }

    @Override
    public void onVideoSpeedClicked() {
        List<OptionItem> items = new ArrayList<>();

        // suppose live stream if buffering near the end
        // boolean isStream = Math.abs(player.getDuration() - player.getCurrentPosition()) < 10_000;
        intSpeedItems(items, new float[]{0.25f, 0.5f, 0.75f, 1.0f, 1.1f, 1.15f, 1.25f, 1.5f, 1.75f, 2f, 2.25f, 2.5f, 2.75f, 3.0f});

        AppSettingsPresenter settingsPresenter = AppSettingsPresenter.instance(mActivity);
        settingsPresenter.clear();
        settingsPresenter.appendRadioCategory(mActivity.getString(R.string.video_speed), items);
        settingsPresenter.showDialog();
    }

    private void intSpeedItems(List<OptionItem> items, float[] speedValues) {
        for (float speed : speedValues) {
            items.add(UiOptionItem.from(
                    String.valueOf(speed),
                    optionItem -> mController.setSpeed(speed),
                    mController.getSpeed() == speed));
        }
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

    private void disposeTimeouts() {
        disableUiAutoHideTimeout();
        disableSuggestionsResetTimeout();
    }

    private void callMediaItemObservable(MediaItemObservable callable) {
        Video video = mController.getVideo();

        if (video == null || video.mediaItem == null) {
            Log.e(TAG, "Seems that video isn't initialized yet. Cancelling...");
            return;
        }

        Observable<Void> observable = callable.call(video.mediaItem);

        observable
                .subscribeOn(Schedulers.newThread())
                .subscribe();
    }

    private interface MediaItemObservable {
        Observable<Void> call(MediaItem item);
    }
}
