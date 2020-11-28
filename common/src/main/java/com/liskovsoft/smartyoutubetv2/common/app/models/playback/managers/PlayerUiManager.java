package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import android.os.Handler;
import android.os.Looper;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackUiController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.SuggestionsLoader.MetadataListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.VideoMenuPresenter;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleManager.SubtitleStyle;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.List;

public class PlayerUiManager extends PlayerEventListenerHelper implements MetadataListener {
    private static final String TAG = PlayerUiManager.class.getSimpleName();
    private static final long SUGGESTIONS_RESET_TIMEOUT_MS = 500;
    private final Handler mHandler;
    private final MediaItemManager mMediaItemManager;
    private boolean mEngineReady;
    private boolean mDebugViewEnabled;
    private PlayerData mPlayerData;
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
        mPlayerData = PlayerData.instance(mActivity);
    }

    @Override
    public void onControlsShown(boolean shown) {
        disableUiAutoHideTimeout();

        if (shown) {
            enableUiAutoHideTimeout();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode) {
        disableUiAutoHideTimeout();
        disableSuggestionsResetTimeout();

        if (KeyHelpers.isBackKey(keyCode)) {
            enableSuggestionsResetTimeout();
        } else if (KeyHelpers.isMenuKey(keyCode)) {
            mController.showControls(!mController.isControlsShown());
        } else if (KeyHelpers.isConfirmKey(keyCode) && !mController.isControlsShown()) {
            switch (mPlayerData.getOKButtonBehavior()) {
                case PlayerData.ONLY_UI:
                    // NOP
                    break;
                case PlayerData.UI_AND_PAUSE:
                    mController.setPlay(false);
                    break;
                case PlayerData.ONLY_PAUSE:
                    mController.setPlay(!mController.isPlaying());
                    return true; // don't show ui
            }
        } else if (KeyHelpers.isStopKey(keyCode)) {
            mController.exit();
            return true;
        }

        enableUiAutoHideTimeout();

        return false;
    }

    @Override
    public void onChannelClicked() {
        ChannelPresenter.instance(mActivity).openChannel(mController.getVideo());
    }

    @Override
    public void onSubtitlesClicked() {
        List<FormatItem> subtitleFormats = mController.getSubtitleFormats();
        List<SubtitleStyle> subtitleStyles = mController.getSubtitleStyles();

        String subtitlesCategoryTitle = mActivity.getString(R.string.subtitle_category_title);
        String subtitleFormatsTitle = mActivity.getString(R.string.subtitle_language);
        String subtitleStyleTitle = mActivity.getString(R.string.subtitle_style);

        AppSettingsPresenter settingsPresenter = AppSettingsPresenter.instance(mActivity);

        settingsPresenter.clear();

        settingsPresenter.appendRadioCategory(subtitleFormatsTitle,
                UiOptionItem.from(subtitleFormats,
                        option -> mController.selectFormat(UiOptionItem.toFormat(option)),
                        mActivity.getString(R.string.subtitles_disabled)));

        settingsPresenter.appendRadioCategory(subtitleStyleTitle, fromSubtitleStyles(subtitleStyles));

        settingsPresenter.showDialog(subtitlesCategoryTitle);
    }
    
    private List<OptionItem> fromSubtitleStyles(List<SubtitleStyle> subtitleStyles) {
        List<OptionItem> styleOptions = new ArrayList<>();

        for (SubtitleStyle subtitleStyle : subtitleStyles) {
            styleOptions.add(UiOptionItem.from(
                    mActivity.getString(subtitleStyle.nameResId),
                    option -> mController.setSubtitleStyle(subtitleStyle),
                    subtitleStyle.equals(mController.getSubtitleStyle())));
        }

        return styleOptions;
    }

    @Override
    public void onPlaylistAddClicked() {
        VideoMenuPresenter mp = VideoMenuPresenter.instance(mActivity);

        mp.showShortMenu(mController.getVideo());
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

        if (mPlayerData.isSeekPreviewEnabled()) {
            mController.loadStoryboard();
        }
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

        showBriefInfo(subscribed);
    }

    private void showBriefInfo(boolean subscribed) {
        if (subscribed) {
            MessageHelpers.showMessage(mActivity, R.string.subscribed_to_channel);
        } else {
            MessageHelpers.showMessage(mActivity, R.string.unsubscribed_to_channel);
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

    @Override
    public void onSearchClicked() {
        SearchPresenter.instance(mActivity).startSearch(null);
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
        if (mEngineReady && mPlayerData.getUIHideTimoutSec() > 0) {
            mHandler.postDelayed(mUiAutoHideHandler, mPlayerData.getUIHideTimoutSec() * 1_000);
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
