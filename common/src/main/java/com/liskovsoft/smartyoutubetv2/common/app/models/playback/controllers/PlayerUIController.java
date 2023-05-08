package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.mediaserviceinterfaces.data.PlaylistInfo;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerEngine;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerUI;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.SuggestionsController.MetadataListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter.VideoMenuCallback;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.SubtitleTrack;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import com.liskovsoft.youtubeapi.service.YouTubeSignInService;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

import java.util.List;

public class PlayerUIController extends PlayerEventListenerHelper implements MetadataListener {
    private static final String TAG = PlayerUIController.class.getSimpleName();
    private static final long SUGGESTIONS_RESET_TIMEOUT_MS = 500;
    private final Handler mHandler;
    private final MediaItemService mMediaItemManager;
    private final VideoLoaderController mVideoLoader;
    private PlayerData mPlayerData;
    private PlayerTweaksData mPlayerTweaksData;
    private List<PlaylistInfo> mPlaylistInfos;
    private boolean mEngineReady;
    private boolean mDebugViewEnabled;
    private boolean mIsMetadataLoaded;
    private final Runnable mSuggestionsResetHandler = () -> getPlayer().resetSuggestedPosition();
    private final Runnable mUiAutoHideHandler = () -> {
        // Playing the video and dialog overlay isn't shown
        if (getPlayer().isPlaying() && !AppDialogPresenter.instance(getActivity()).isDialogShown()) {
            if (!getPlayer().isSuggestionsShown()) { // don't hide when suggestions is shown
                getPlayer().showOverlay(false);
            }
        } else {
            // in seeking state? doing recheck...
            enableUiAutoHideTimeout();
        }
    };

    public PlayerUIController(VideoLoaderController videoLoader) {
        mVideoLoader = videoLoader;
        mHandler = new Handler(Looper.getMainLooper());

        MediaService service = YouTubeMediaService.instance();
        mMediaItemManager = service.getMediaItemService();
    }

    @Override
    public void onInitDone() {
        mPlayerData = PlayerData.instance(getActivity());
        mPlayerTweaksData = PlayerTweaksData.instance(getActivity());

        // Could be set once per activity creation (view layout stuff)
        getPlayer().setVideoZoomMode(mPlayerData.getVideoZoomMode());
        getPlayer().setVideoZoom(mPlayerData.getVideoZoom());
        getPlayer().setVideoAspectRatio(mPlayerData.getVideoAspectRatio());
        getPlayer().setVideoRotation(mPlayerData.getVideoRotation());
    }

    @Override
    public void openVideo(Video item) {
        enableUiAutoHideTimeout();

        if (item != null && getPlayer() != null && !item.equals(getPlayer().getVideo())) {
            mIsMetadataLoaded = false; // metadata isn't loaded yet at this point
            resetButtonStates();
        }
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
        startTempBackgroundMode(ChannelPresenter.class);
        ChannelPresenter.instance(getActivity()).openChannel(getPlayer().getVideo());
    }

    @Override
    public void onSubtitleClicked(boolean enabled) {
        // First run
        if (FormatItem.SUBTITLE_DEFAULT.equals(mPlayerData.getLastSubtitleFormat())) {
            onSubtitleLongClicked(enabled);
            return;
        }

        // Only default in the list
        if (getPlayer().getSubtitleFormats() == null || getPlayer().getSubtitleFormats().size() == 1) {
            return;
        }

        // Match found
        if (getPlayer().getSubtitleFormats().contains(mPlayerData.getLastSubtitleFormat())) {
            getPlayer().setFormat(enabled ? FormatItem.SUBTITLE_DEFAULT : mPlayerData.getLastSubtitleFormat());
            getPlayer().setSubtitleButtonState(!FormatItem.SUBTITLE_DEFAULT.equals(mPlayerData.getLastSubtitleFormat()) && !enabled);
        } else {
            // Match not found
            onSubtitleLongClicked(enabled);
        }
    }

    @Override
    public void onSubtitleLongClicked(boolean enabled) {
        String subtitlesOrigCategoryTitle = getActivity().getString(R.string.subtitle_category_title);
        String subtitlesAutoCategoryTitle = subtitlesOrigCategoryTitle + " (Auto)";

        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getActivity());

        settingsPresenter.appendSingleButton(UiOptionItem.from(subtitlesOrigCategoryTitle, optionItem -> {
            List<FormatItem> subtitleFormats = getPlayer().getSubtitleFormats();
            List<FormatItem> subtitleOrigFormats = Helpers.filter(subtitleFormats,
                    value -> value.isDefault() || !SubtitleTrack.isAuto(value.getLanguage()));
            settingsPresenter.appendRadioCategory(subtitlesOrigCategoryTitle,
                    UiOptionItem.from(subtitleOrigFormats,
                            option -> getPlayer().setFormat(UiOptionItem.toFormat(option)),
                            getActivity().getString(R.string.subtitles_disabled)));
            settingsPresenter.showDialog();
        }));

        settingsPresenter.appendSingleButton(UiOptionItem.from(subtitlesAutoCategoryTitle, optionItem -> {
            List<FormatItem> subtitleFormats = getPlayer().getSubtitleFormats();
            List<FormatItem> subtitleAutoFormats = Helpers.filter(subtitleFormats,
                    value -> value.isDefault() || SubtitleTrack.isAuto(value.getLanguage()));
            settingsPresenter.appendRadioCategory(subtitlesAutoCategoryTitle,
                    UiOptionItem.from(subtitleAutoFormats,
                            option -> getPlayer().setFormat(UiOptionItem.toFormat(option)),
                            getActivity().getString(R.string.subtitles_disabled)));
            settingsPresenter.showDialog();
        }));

        OptionCategory stylesCategory = AppDialogUtil.createSubtitleStylesCategory(getActivity(), mPlayerData);
        settingsPresenter.appendRadioCategory(stylesCategory.title, stylesCategory.options);

        OptionCategory sizeCategory = AppDialogUtil.createSubtitleSizeCategory(getActivity(), mPlayerData);
        settingsPresenter.appendRadioCategory(sizeCategory.title, sizeCategory.options);

        OptionCategory positionCategory = AppDialogUtil.createSubtitlePositionCategory(getActivity(), mPlayerData);
        settingsPresenter.appendRadioCategory(positionCategory.title, positionCategory.options);

        settingsPresenter.showDialog(subtitlesOrigCategoryTitle, this::setSubtitleButtonState);
    }

    @Override
    public void onPlaylistAddClicked() {
        if (mPlaylistInfos == null) {
            AppDialogUtil.showAddToPlaylistDialog(getActivity(), getPlayer().getVideo(),
                    null);
        } else {
            AppDialogUtil.showAddToPlaylistDialog(getActivity(), getPlayer().getVideo(),
                    null, mPlaylistInfos, this::setPlaylistAddButtonState);
        }
    }

    @Override
    public void onDebugInfoClicked(boolean enabled) {
        mDebugViewEnabled = enabled;
        getPlayer().showDebugInfo(enabled);
    }

    @Override
    public void onEngineInitialized() {
        mEngineReady = true;

        if (AppDialogPresenter.instance(getActivity()).isDialogShown()) {
            // Activate debug infos/show ui after engine restarting (buffering, sound shift, error?).
            getPlayer().showOverlay(true);
            getPlayer().showDebugInfo(mDebugViewEnabled);
            getPlayer().setDebugButtonState(mDebugViewEnabled);
        }
    }

    @Override
    public void onVideoLoaded(Video item) {
        getPlayer().updateEndingTime();
    }

    @Override
    public void onSeekEnd() {
        getPlayer().updateEndingTime();
    }

    @Override
    public void onViewResumed() {
        if (getPlayer() == null) {
            return;
        }

        // Reset temp mode.
        SearchData.instance(getActivity()).setTempBackgroundModeClass(null);

        // Activate debug infos when restoring after PIP.
        getPlayer().showDebugInfo(mDebugViewEnabled);
        getPlayer().setDebugButtonState(mDebugViewEnabled);
        getPlayer().showSubtitles(true);

        // Maybe dialog just closed. Reset timeout just in case.
        enableUiAutoHideTimeout();
    }

    @Override
    public void onViewPaused() {
        if (getPlayer().isInPIPMode()) {
            // UI couldn't be properly displayed in PIP mode
            getPlayer().showOverlay(false);
            getPlayer().showDebugInfo(false);
            getPlayer().setDebugButtonState(false);
            getPlayer().showSubtitles(false);
        }
    }

    private void resetButtonStates() {
        getPlayer().setLikeButtonState(false);
        getPlayer().setDislikeButtonState(false);
        getPlayer().setSubscribeButtonState(false);
        getPlayer().setChannelIcon(null);
        getPlayer().setPlaylistAddButtonState(false);
        getPlayer().setSubtitleButtonState(false);
        getPlayer().setSpeedButtonState(false);
        getPlayer().setChatButtonState(false);
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
        if (mPlayerData.getSeekPreviewMode() != PlayerData.SEEK_PREVIEW_NONE) {
            getPlayer().loadStoryboard();
        }
        getPlayer().setLikeButtonState(metadata.getLikeStatus() == MediaItemMetadata.LIKE_STATUS_LIKE);
        getPlayer().setDislikeButtonState(metadata.getLikeStatus() == MediaItemMetadata.LIKE_STATUS_DISLIKE);
        getPlayer().setSubscribeButtonState(metadata.isSubscribed());
        if (mPlayerTweaksData.isRealChannelIconEnabled()) {
            getPlayer().setChannelIcon(metadata.getAuthorImageUrl());
        }
        setPlaylistAddButtonStateCached();
        setSubtitleButtonState();
        getPlayer().setButtonState(R.id.action_rotate, mPlayerData.getVideoRotation() == 0 ? PlayerUI.BUTTON_OFF : PlayerUI.BUTTON_ON);
    }

    @Override
    public void onSuggestionItemLongClicked(Video item) {
        VideoMenuPresenter.instance(getActivity()).showMenu(item, (videoItem, action) -> {
            String title = getActivity().getString(R.string.action_playback_queue);
            int id = title.hashCode();

            if (action == VideoMenuCallback.ACTION_REMOVE_FROM_QUEUE) {
                VideoGroup group = VideoGroup.from(videoItem);
                group.setTitle(title);
                group.setId(id);
                getPlayer().removeSuggestions(group);
            } else if (action == VideoMenuCallback.ACTION_ADD_TO_QUEUE) {
                Video newItem = videoItem.copy();
                VideoGroup group = VideoGroup.from(newItem, 0);
                group.setTitle(title);
                group.setId(id);
                newItem.setGroup(group);
                getPlayer().updateSuggestions(group);
            }
        });
    }

    @Override
    public void onSubscribeClicked(boolean subscribed) {
        if (!mIsMetadataLoaded) {
            MessageHelpers.showMessage(getActivity(), R.string.wait_data_loading);
            getPlayer().setSubscribeButtonState(!subscribed);
            return;
        }

        if (!YouTubeSignInService.instance().isSigned()) {
            getPlayer().setSubscribeButtonState(false);
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
            getPlayer().setDislikeButtonState(!dislike);
            return;
        }

        if (!YouTubeSignInService.instance().isSigned()) {
            getPlayer().setDislikeButtonState(false);
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
            getPlayer().setLikeButtonState(!like);
            return;
        }

        if (!YouTubeSignInService.instance().isSigned()) {
            getPlayer().setLikeButtonState(false);
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
    public void onSeekIntervalClicked() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getActivity());

        AppDialogUtil.appendSeekIntervalDialogItems(getActivity(), settingsPresenter, mPlayerData, true);

        settingsPresenter.showDialog();
    }

    @Override
    public void onVideoInfoClicked() {
        if (!mIsMetadataLoaded) {
            MessageHelpers.showMessage(getActivity(), R.string.wait_data_loading);
            return;
        }

        Video video = getPlayer().getVideo();

        String description = video.description;

        if (description == null || description.isEmpty()) {
            MessageHelpers.showMessage(getActivity(), R.string.description_not_found);
            return;
        }

        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(getActivity());

        String title = String.format("%s - %s", video.getTitle(), video.getAuthor());

        dialogPresenter.appendLongTextCategory(title, UiOptionItem.from(description));

        dialogPresenter.showDialog(title);
    }

    @Override
    public void onShareLinkClicked() {
        Video video = getPlayer().getVideo();

        if (video == null) {
            return;
        }

        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(getActivity());

        int positionSec = Utils.toSec(getPlayer().getPositionMs());
        AppDialogUtil.appendShareLinkDialogItem(getActivity(), dialogPresenter, getPlayer().getVideo(), positionSec);
        AppDialogUtil.appendShareQRLinkDialogItem(getActivity(), dialogPresenter, getPlayer().getVideo(), positionSec);
        AppDialogUtil.appendShareEmbedLinkDialogItem(getActivity(), dialogPresenter, getPlayer().getVideo(), positionSec);

        dialogPresenter.showDialog(getPlayer().getVideo().title);

        //if (video.videoId != null) {
        //    Utils.displayShareVideoDialog(getActivity(), video.videoId, (int)(getController().getPositionMs() / 1_000));
        //} else if (video.channelId != null) {
        //    Utils.displayShareChannelDialog(getActivity(), video.channelId);
        //}
    }

    @Override
    public void onSearchClicked() {
        startTempBackgroundMode(SearchPresenter.class);
        SearchPresenter.instance(getActivity()).startSearch(null);
    }

    @Override
    public void onVideoZoomClicked() {
        OptionCategory videoZoomCategory = AppDialogUtil.createVideoZoomCategory(
                getActivity(), mPlayerData, () -> {
                    getPlayer().setVideoZoomMode(mPlayerData.getVideoZoomMode());
                    getPlayer().setVideoZoom(mPlayerData.getVideoZoom());
                    getPlayer().showControls(false);
                });

        OptionCategory videoAspectCategory = AppDialogUtil.createVideoAspectCategory(
                getActivity(), mPlayerData, () -> getPlayer().setVideoAspectRatio(mPlayerData.getVideoAspectRatio()));

        OptionCategory videoRotateCategory = AppDialogUtil.createVideoRotateCategory(
                getActivity(), mPlayerData, () -> getPlayer().setVideoRotation(mPlayerData.getVideoRotation()));

        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getActivity());
        settingsPresenter.appendRadioCategory(videoAspectCategory.title, videoAspectCategory.options);
        settingsPresenter.appendRadioCategory(videoZoomCategory.title, videoZoomCategory.options);
        settingsPresenter.appendRadioCategory(videoRotateCategory.title, videoRotateCategory.options);
        settingsPresenter.showDialog(getActivity().getString(R.string.video_aspect));
    }

    @Override
    public void onPipClicked() {
        getPlayer().showOverlay(false);
        getPlayer().setBackgroundMode(
                Helpers.isPictureInPictureSupported(getActivity()) ?
                        PlayerEngine.BACKGROUND_MODE_PIP : PlayerEngine.BACKGROUND_MODE_SOUND
        );
        getPlayer().finish();
    }

    @Override
    public void onScreenOffClicked() {
        if (getActivity() instanceof MotherActivity) {
            ((MotherActivity) getActivity()).getScreensaverManager().doScreenOff();
        }
    }

    @Override
    public void onRepeatModeClicked(int modeIndex) {
        mPlayerData.setRepeatMode(modeIndex);
        //Utils.showRepeatInfo(getActivity(), modeIndex);
    }

    @Override
    public void onButtonClicked(int buttonId, int buttonState) {
        if (buttonId == R.id.action_rotate) {
            int oldRotation = mPlayerData.getVideoRotation();
            int rotation = oldRotation == 0 ? 90 : oldRotation == 90 ? 180 : oldRotation == 180 ? 270 : 0;
            getPlayer().setVideoRotation(rotation);
            getPlayer().setButtonState(buttonId, rotation == 0 ? PlayerUI.BUTTON_OFF : PlayerUI.BUTTON_ON);
            mPlayerData.setVideoRotation(rotation);
        }
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
        Video video = getPlayer().getVideo();

        if (video == null) {
            Log.e(TAG, "Seems that video isn't initialized yet. Cancelling...");
            return;
        }

        Observable<Void> observable = callable.call(video.mediaItem != null ? video.mediaItem : video.toMediaItem());

        RxHelper.execute(observable);
    }

    private boolean handleBackKey(int keyCode) {
        if (KeyHelpers.isBackKey(keyCode)) {
            enableSuggestionsResetTimeout();

            // Close future stream with single back click
            if (!getPlayer().containsMedia()) {
                getPlayer().finish();
            }
        }

        return false;
    }

    private boolean handleMenuKey(int keyCode) {
        boolean controlsShown = getPlayer().isOverlayShown();
        boolean suggestionsShown = getPlayer().isSuggestionsShown();

        if (KeyHelpers.isMenuKey(keyCode) && !suggestionsShown) {
            getPlayer().showOverlay(!controlsShown);

            if (controlsShown) {
                enableSuggestionsResetTimeout();
            }
        }

        return false;
    }

    private boolean handleConfirmKey(int keyCode) {
        boolean controlsShown = getPlayer().isOverlayShown();

        if (KeyHelpers.isConfirmKey(keyCode) && !controlsShown) {
            switch (mPlayerData.getOKButtonBehavior()) {
                case PlayerData.ONLY_UI:
                    getPlayer().showOverlay(true);
                    return true; // don't show ui
                case PlayerData.UI_AND_PAUSE:
                    // NOP
                    break;
                case PlayerData.ONLY_PAUSE:
                    getPlayer().setPlayWhenReady(!getPlayer().getPlayWhenReady());
                    return true; // don't show ui
            }
        }

        return false;
    }

    private boolean handleStopKey(int keyCode) {
        if (KeyHelpers.isStopKey(keyCode)) {
            getPlayer().finish();
            return true;
        }

        return false;
    }

    private boolean handleNumKeys(int keyCode) {
        if (mPlayerData.isNumberKeySeekEnabled() && keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            if (getPlayer() != null && getPlayer().getDurationMs() > 0) {
                float seekPercent = (keyCode - KeyEvent.KEYCODE_0) / 10f;
                getPlayer().setPositionMs((long)(getPlayer().getDurationMs() * seekPercent));
            }
        }

        return false;
    }

    private boolean handlePlayPauseKey(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            getPlayer().setPlayWhenReady(!getPlayer().getPlayWhenReady());
            enableUiAutoHideTimeout(); // TODO: move out somehow
            return true;
        }

        return false;
    }

    private interface MediaItemObservable {
        Observable<Void> call(MediaItem item);
    }

    private void setPlaylistAddButtonStateCached() {
        String videoId = getPlayer().getVideo().videoId;
        mPlaylistInfos = null;
        Disposable playlistsInfoAction =
                YouTubeMediaService.instance().getMediaItemService().getPlaylistsInfoObserve(videoId)
                        .subscribe(
                                videoPlaylistInfos -> {
                                    mPlaylistInfos = videoPlaylistInfos;
                                    setPlaylistAddButtonState();
                                },
                                error -> Log.e(TAG, "Add to recent playlist error: %s", error.getMessage())
                        );
    }

    private void setPlaylistAddButtonState() {
        if (mPlaylistInfos == null || getPlayer() == null) {
            return;
        }

        boolean isSelected = false;
        for (PlaylistInfo playlistInfo : mPlaylistInfos) {
            if (playlistInfo.isSelected()) {
                isSelected = true;
                break;
            }
        }

        getPlayer().setPlaylistAddButtonState(isSelected);
    }

    private void setSubtitleButtonState() {
        if (getPlayer() == null) {
            return;
        }

        List<FormatItem> subtitleFormats = getPlayer().getSubtitleFormats();

        if (subtitleFormats == null) {
            return;
        }

        boolean isSelected = false;

        for (FormatItem subtitle : subtitleFormats) {
            if (subtitle.isSelected() && !subtitle.isDefault()) {
                isSelected = true;
                break;
            }
        }

        getPlayer().setSubtitleButtonState(isSelected);
    }

    private void startTempBackgroundMode(Class<?> clazz) {
        SearchData searchData = SearchData.instance(getActivity());
        if (searchData.isTempBackgroundModeEnabled()) {
            searchData.setTempBackgroundModeClass(clazz);
            onPipClicked();
        }
    }
}
