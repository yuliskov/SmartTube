package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.mediaserviceinterfaces.data.VideoPlaylistInfo;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackEngineController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.SuggestionsLoaderManager.MetadataListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter.VideoMenuCallback;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import com.liskovsoft.youtubeapi.service.YouTubeSignInManager;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.List;

public class PlayerUIManager extends PlayerEventListenerHelper implements MetadataListener {
    private static final String TAG = PlayerUIManager.class.getSimpleName();
    private static final long SUGGESTIONS_RESET_TIMEOUT_MS = 500;
    private final Handler mHandler;
    private final MediaItemManager mMediaItemManager;
    private final VideoLoaderManager mVideoLoader;
    private PlayerData mPlayerData;
    private List<VideoPlaylistInfo> mVideoPlaylistInfos;
    private boolean mEngineReady;
    private boolean mDebugViewEnabled;
    private boolean mIsMetadataLoaded;
    private final Runnable mSuggestionsResetHandler = () -> getController().resetSuggestedPosition();
    private final Runnable mUiAutoHideHandler = () -> {
        // Playing the video and dialog overlay isn't shown
        if (getController().isPlaying() && !AppDialogPresenter.instance(getActivity()).isDialogShown()) {
            if (!getController().isSuggestionsShown()) { // don't hide when suggestions is shown
                getController().showOverlay(false);
            }
        } else {
            // in seeking state? doing recheck...
            enableUiAutoHideTimeout();
        }
    };

    public PlayerUIManager(VideoLoaderManager videoLoader) {
        mVideoLoader = videoLoader;
        mHandler = new Handler(Looper.getMainLooper());

        MediaService service = YouTubeMediaService.instance();
        mMediaItemManager = service.getMediaItemManager();
    }

    @Override
    public void onInitDone() {
        mPlayerData = PlayerData.instance(getActivity());

        // Could be set once per activity creation (view layout stuff)
        getController().setVideoZoomMode(mPlayerData.getVideoZoomMode());
        getController().setVideoAspectRatio(mPlayerData.getVideoAspectRatio());
    }

    @Override
    public void openVideo(Video item) {
        enableUiAutoHideTimeout();
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

        boolean isHandled = handleBackKey(keyCode) || handleMenuKey(keyCode) ||
                handleConfirmKey(keyCode) || handleStopKey(keyCode) || handleNumKeys(keyCode) ||
                handlePlayPauseKey(keyCode);

        if (isHandled) {
            return true; // don't show UI
        }

        enableUiAutoHideTimeout();

        return false;
    }

    @Override
    public void onChannelClicked() {
        ChannelPresenter.instance(getActivity()).openChannel(getController().getVideo());
    }

    @Override
    public void onSubtitlesClicked() {
        List<FormatItem> subtitleFormats = getController().getSubtitleFormats();

        String subtitlesCategoryTitle = getActivity().getString(R.string.subtitle_category_title);
        String subtitleFormatsTitle = getActivity().getString(R.string.subtitle_language);

        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getActivity());

        settingsPresenter.clear();

        settingsPresenter.appendRadioCategory(subtitleFormatsTitle,
                UiOptionItem.from(subtitleFormats,
                        option -> getController().setFormat(UiOptionItem.toFormat(option)),
                        getActivity().getString(R.string.subtitles_disabled)));

        settingsPresenter.showDialog(subtitlesCategoryTitle);
    }

    @Override
    public void onPlaylistAddClicked() {
        if (mVideoPlaylistInfos == null) {
            AppDialogUtil.showAddToPlaylistDialog(getActivity(), getController().getVideo(),
                    null);
        } else {
            AppDialogUtil.showAddToPlaylistDialog(getActivity(), getController().getVideo(),
                    null, mVideoPlaylistInfos, this::setPlaylistAddStateCached);
        }
    }

    @Override
    public void onDebugInfoClicked(boolean enabled) {
        mDebugViewEnabled = enabled;
        getController().showDebugInfo(enabled);
    }

    @Override
    public void onEngineInitialized() {
        mEngineReady = true;

        if (AppDialogPresenter.instance(getActivity()).isDialogShown()) {
            // Activate debug infos/show ui after engine restarting (buffering, sound shift, error?).
            getController().showOverlay(true);
            getController().showDebugInfo(mDebugViewEnabled);
            getController().setDebugButtonState(mDebugViewEnabled);
        }
    }

    @Override
    public void onVideoLoaded(Video item) {
        mIsMetadataLoaded = false; // metadata isn't loaded yet at this point
        resetButtonStates();

        if (mPlayerData.getSeekPreviewMode() != PlayerData.SEEK_PREVIEW_NONE) {
            getController().loadStoryboard();
        }

        getController().updateEndingTime();
    }

    @Override
    public void onSeekEnd() {
        getController().updateEndingTime();
    }

    @Override
    public void onViewResumed() {
        // Activate debug infos when restoring after PIP.
        getController().showDebugInfo(mDebugViewEnabled);
        getController().setDebugButtonState(mDebugViewEnabled);
        getController().showSubtitles(true);

        // Maybe dialog just closed. Reset timeout just in case.
        enableUiAutoHideTimeout();
    }

    @Override
    public void onViewPaused() {
        if (getController().isInPIPMode()) {
            // UI couldn't be properly displayed in PIP mode
            getController().showOverlay(false);
            getController().showDebugInfo(false);
            getController().setDebugButtonState(false);
            getController().showSubtitles(false);
        }
    }

    private void resetButtonStates() {
        getController().setLikeButtonState(false);
        getController().setDislikeButtonState(false);
        getController().setSubscribeButtonState(false);
    }

    @Override
    public void onEngineReleased() {
        Log.d(TAG, "Engine released. Disabling all callbacks...");
        mEngineReady = false;

        disposeTimeouts();
    }

    @Override
    public void onMetadata(MediaItemMetadata metadata) {
        mIsMetadataLoaded = true;
        getController().setLikeButtonState(metadata.getLikeStatus() == MediaItemMetadata.LIKE_STATUS_LIKE);
        getController().setDislikeButtonState(metadata.getLikeStatus() == MediaItemMetadata.LIKE_STATUS_DISLIKE);
        getController().setSubscribeButtonState(metadata.isSubscribed());
        getController().setChannelIcon(metadata.getAuthorImageUrl());
        setPlaylistAddStateCached();
    }

    @Override
    public void onSuggestionItemLongClicked(Video item) {
        VideoMenuPresenter.instance(getActivity()).showMenu(item);
    }

    @Override
    public void onSubscribeClicked(boolean subscribed) {
        if (!mIsMetadataLoaded) {
            MessageHelpers.showMessage(getActivity(), R.string.wait_data_loading);
            getController().setSubscribeButtonState(!subscribed);
            return;
        }

        if (!YouTubeSignInManager.instance().isSigned()) {
            getController().setSubscribeButtonState(false);
            MessageHelpers.showMessage(getActivity(), R.string.msg_signed_users_only);
            return;
        }

        if (subscribed) {
            callMediaItemObservable(mMediaItemManager::subscribeObserve);
        } else {
            callMediaItemObservable(mMediaItemManager::unsubscribeObserve);
        }
    }

    @Override
    public void onDislikeClicked(boolean dislike) {
        if (!mIsMetadataLoaded) {
            MessageHelpers.showMessage(getActivity(), R.string.wait_data_loading);
            getController().setDislikeButtonState(!dislike);
            return;
        }

        if (!YouTubeSignInManager.instance().isSigned()) {
            getController().setDislikeButtonState(false);
            MessageHelpers.showMessage(getActivity(), R.string.msg_signed_users_only);
            return;
        }

        if (dislike) {
            callMediaItemObservable(mMediaItemManager::setDislikeObserve);
        } else {
            callMediaItemObservable(mMediaItemManager::removeDislikeObserve);
        }
    }

    @Override
    public void onLikeClicked(boolean like) {
        if (!mIsMetadataLoaded) {
            MessageHelpers.showMessage(getActivity(), R.string.wait_data_loading);
            getController().setLikeButtonState(!like);
            return;
        }

        if (!YouTubeSignInManager.instance().isSigned()) {
            getController().setLikeButtonState(false);
            MessageHelpers.showMessage(getActivity(), R.string.msg_signed_users_only);
            return;
        }

        if (like) {
            callMediaItemObservable(mMediaItemManager::setLikeObserve);
        } else {
            callMediaItemObservable(mMediaItemManager::removeLikeObserve);
        }
    }

    @Override
    public void onVideoSpeedClicked() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getActivity());
        settingsPresenter.clear();

        // suppose live stream if buffering near the end
        // boolean isStream = Math.abs(player.getDuration() - player.getCurrentPosition()) < 10_000;
        AppDialogUtil.appendSpeedDialogItems(getActivity(), settingsPresenter, mPlayerData, getController());

        settingsPresenter.showDialog();
    }

    @Override
    public void onSeekIntervalClicked() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getActivity());
        settingsPresenter.clear();

        AppDialogUtil.appendSeekIntervalDialogItems(getActivity(), settingsPresenter, mPlayerData, true);

        settingsPresenter.showDialog();
    }

    @Override
    public void onVideoInfoClicked() {
        if (!mIsMetadataLoaded) {
            MessageHelpers.showMessage(getActivity(), R.string.wait_data_loading);
            return;
        }

        String description = getController().getVideo().description;

        if (description == null || description.isEmpty()) {
            MessageHelpers.showMessage(getActivity(), R.string.description_not_found);
            return;
        }

        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(getActivity());

        dialogPresenter.clear();

        String title = getController().getVideo().title;

        dialogPresenter.appendLongTextCategory(title, UiOptionItem.from(description, null));

        dialogPresenter.showDialog(title);
    }

    @Override
    public void onShareLinkClicked() {
        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(getActivity());

        dialogPresenter.clear();

        AppDialogUtil.appendShareDialogItems(getActivity(), dialogPresenter, getController().getVideo());

        dialogPresenter.showDialog(getController().getVideo().title);
    }

    @Override
    public void onSearchClicked() {
        if (SearchData.instance(getActivity()).isBackgroundPlaybackEnabled()) {
            onPipClicked();
        }
        SearchPresenter.instance(getActivity()).startSearch(null);
    }

    @Override
    public void onVideoZoomClicked() {
        OptionCategory videoZoomCategory = AppDialogUtil.createVideoZoomCategory(
                getActivity(), mPlayerData, () -> getController().setVideoZoomMode(mPlayerData.getVideoZoomMode()));

        OptionCategory videoAspectCategory = AppDialogUtil.createVideoAspectCategory(
                getActivity(), mPlayerData, () -> getController().setVideoAspectRatio(mPlayerData.getVideoAspectRatio()));

        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getActivity());
        settingsPresenter.clear();
        settingsPresenter.appendRadioCategory(videoAspectCategory.title, videoAspectCategory.options);
        settingsPresenter.appendRadioCategory(videoZoomCategory.title, videoZoomCategory.options);
        settingsPresenter.showDialog(getActivity().getString(R.string.video_aspect));
    }

    @Override
    public void onPipClicked() {
        getController().showOverlay(false);
        getController().setBackgroundMode(
                Helpers.isPictureInPictureSupported(getActivity()) ?
                        PlaybackEngineController.BACKGROUND_MODE_PIP : PlaybackEngineController.BACKGROUND_MODE_SOUND
        );
        getController().finish();
    }

    @Override
    public void onScreenOffClicked() {
        if (getActivity() instanceof MotherActivity) {
            ((MotherActivity) getActivity()).getScreensaverManager().doScreenOff();
        }
    }

    @Override
    public void onRepeatModeClicked(int modeIndex) {
        mPlayerData.setPlaybackMode(modeIndex);
        //Utils.showRepeatInfo(getActivity(), modeIndex);
    }

    private void disableUiAutoHideTimeout() {
        Log.d(TAG, "Stopping auto hide ui timer...");
        mHandler.removeCallbacks(mUiAutoHideHandler);
    }

    private void enableUiAutoHideTimeout() {
        Log.d(TAG, "Starting auto hide ui timer...");
        disableUiAutoHideTimeout();
        if (mEngineReady && mPlayerData.getUIHideTimoutSec() > 0) {
            mHandler.postDelayed(mUiAutoHideHandler, mPlayerData.getUIHideTimoutSec() * 1_000L);
        }
    }

    private void disableSuggestionsResetTimeout() {
        Log.d(TAG, "Stopping reset position timer...");
        mHandler.removeCallbacks(mSuggestionsResetHandler);
    }

    private void enableSuggestionsResetTimeout() {
        Log.d(TAG, "Starting reset position timer...");
        disableSuggestionsResetTimeout();
        if (mEngineReady) {
            mHandler.postDelayed(mSuggestionsResetHandler, SUGGESTIONS_RESET_TIMEOUT_MS);
        }
    }

    private void disposeTimeouts() {
        disableUiAutoHideTimeout();
        disableSuggestionsResetTimeout();
    }

    private void callMediaItemObservable(MediaItemObservable callable) {
        Video video = getController().getVideo();

        if (video == null || video.mediaItem == null) {
            Log.e(TAG, "Seems that video isn't initialized yet. Cancelling...");
            return;
        }

        Observable<Void> observable = callable.call(video.mediaItem);

        RxUtils.execute(observable);
    }

    private boolean handleBackKey(int keyCode) {
        if (KeyHelpers.isBackKey(keyCode)) {
            enableSuggestionsResetTimeout();

            // Close future stream with single back click
            if (!getController().containsMedia()) {
                getController().finish();
            }
        }

        return false;
    }

    private boolean handleMenuKey(int keyCode) {
        boolean controlsShown = getController().isOverlayShown();
        boolean suggestionsShown = getController().isSuggestionsShown();

        if (KeyHelpers.isMenuKey(keyCode) && !suggestionsShown) {
            getController().showOverlay(!controlsShown);

            if (controlsShown) {
                enableSuggestionsResetTimeout();
            }
        }

        return false;
    }

    private boolean handleConfirmKey(int keyCode) {
        boolean controlsShown = getController().isOverlayShown();

        if (KeyHelpers.isConfirmKey(keyCode) && !controlsShown) {
            switch (mPlayerData.getOKButtonBehavior()) {
                case PlayerData.ONLY_UI:
                    getController().showOverlay(true);
                    return true; // don't show ui
                case PlayerData.UI_AND_PAUSE:
                    // NOP
                    break;
                case PlayerData.ONLY_PAUSE:
                    getController().setPlay(!getController().getPlay());
                    return true; // don't show ui
            }
        }

        return false;
    }

    private boolean handleStopKey(int keyCode) {
        if (KeyHelpers.isStopKey(keyCode)) {
            getController().finish();
            return true;
        }

        return false;
    }

    private boolean handleNumKeys(int keyCode) {
        if (mPlayerData.isNumberKeySeekEnabled() && keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            if (getController() != null && getController().getLengthMs() > 0) {
                float seekPercent = (keyCode - KeyEvent.KEYCODE_0) / 10f;
                getController().setPositionMs((long)(getController().getLengthMs() * seekPercent));
            }
        }

        return false;
    }

    private boolean handlePlayPauseKey(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            getController().setPlay(!getController().getPlay());
            enableUiAutoHideTimeout(); // TODO: move out somehow
            return true;
        }

        return false;
    }

    private interface MediaItemObservable {
        Observable<Void> call(MediaItem item);
    }

    private void setPlaylistAddStateCached() {
        String videoId = getController().getVideo().videoId;
        mVideoPlaylistInfos = null;
        Disposable playlistsInfoAction =
                YouTubeMediaService.instance().getMediaItemManager().getVideoPlaylistsInfoObserve(videoId)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                videoPlaylistInfos -> {
                                    mVideoPlaylistInfos = videoPlaylistInfos;
                                    boolean isSelected = false;
                                    for (VideoPlaylistInfo playlistInfo : videoPlaylistInfos) {
                                        if (playlistInfo.isSelected()) {
                                            isSelected = true;
                                            break;
                                        }
                                    }

                                    getController().setPlaylistAddState(isSelected);
                                },
                                error -> Log.e(TAG, "Add to recent playlist error: %s", error.getMessage())
                        );
    }
}
