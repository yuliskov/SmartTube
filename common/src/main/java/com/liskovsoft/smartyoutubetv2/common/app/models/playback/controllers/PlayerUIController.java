package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import com.liskovsoft.mediaserviceinterfaces.yt.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.yt.ServiceManager;
import com.liskovsoft.mediaserviceinterfaces.yt.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.yt.data.MediaItemMetadata;
import com.liskovsoft.mediaserviceinterfaces.yt.data.NotificationState;
import com.liskovsoft.mediaserviceinterfaces.yt.data.PlaylistInfo;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerEngineConstants;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerUI;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter.VideoMenuCallback;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AutoFrameRateSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.SubtitleTrack;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.common.misc.ScreensaverManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import com.liskovsoft.youtubeapi.service.YouTubeSignInService;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

import java.util.ArrayList;
import java.util.List;

public class PlayerUIController extends PlayerEventListenerHelper {
    private static final String TAG = PlayerUIController.class.getSimpleName();
    private static final long SUGGESTIONS_RESET_TIMEOUT_MS = 500;
    private final Handler mHandler;
    private final MediaItemService mMediaItemService;
    private SuggestionsController mSuggestionsController;
    private PlayerData mPlayerData;
    private PlayerTweaksData mPlayerTweaksData;
    private List<PlaylistInfo> mPlaylistInfos;
    private FormatItem mAudioFormat = FormatItem.AUDIO_HQ_MP4A;
    private boolean mEngineReady;
    private boolean mDebugViewEnabled;
    private boolean mIsMetadataLoaded;
    private long mOverlayHideTimeMs;
    private final Runnable mSuggestionsResetHandler = () -> getPlayer().resetSuggestedPosition();
    private final Runnable mUiAutoHideHandler = () -> {
        // Playing the video and dialog overlay isn't shown
        if (getPlayer().isPlaying() && !AppDialogPresenter.instance(getContext()).isDialogShown()) {
            if (getPlayer().isControlsShown()) { // don't hide when suggestions is shown
                getPlayer().showOverlay(false);
                mOverlayHideTimeMs = System.currentTimeMillis();
            }
        } else {
            // in seeking state? doing recheck...
            enableUiAutoHideTimeout();
        }
    };

    public PlayerUIController() {
        mHandler = new Handler(Looper.getMainLooper());

        ServiceManager service = YouTubeServiceManager.instance();
        mMediaItemService = service.getMediaItemService();
    }

    @Override
    public void onInit() {
        mSuggestionsController = getController(SuggestionsController.class);
        mPlayerData = PlayerData.instance(getContext());
        mPlayerTweaksData = PlayerTweaksData.instance(getContext());

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
                handlePlayPauseKey(keyCode) || handleShortsNavigation(keyCode);

        if (isHandled) {
            return true; // don't show UI
        }

        enableUiAutoHideTimeout();

        return false;
    }

    @Override
    public void onChannelClicked() {
        startTempBackgroundMode(ChannelPresenter.class);
        ChannelPresenter.instance(getContext()).openChannel(getPlayer().getVideo());
    }

    @Override
    public void onSubtitleClicked(boolean enabled) {
        // First run
        if (FormatItem.SUBTITLE_NONE.equals(mPlayerData.getLastSubtitleFormat())) {
            onSubtitleLongClicked(enabled);
            return;
        }

        // Only default in the list
        if (getPlayer().getSubtitleFormats() == null || getPlayer().getSubtitleFormats().size() == 1) {
            return;
        }

        // Match found
        if (getPlayer().getSubtitleFormats().contains(mPlayerData.getLastSubtitleFormat())) {
            FormatItem format = enabled ? FormatItem.SUBTITLE_NONE : mPlayerData.getLastSubtitleFormat();
            getPlayer().setFormat(format);
            mPlayerData.setFormat(format);
            getPlayer().setSubtitleButtonState(!FormatItem.SUBTITLE_NONE.equals(mPlayerData.getLastSubtitleFormat()) && !enabled);
            enableSubtitleForChannel(!enabled);
        } else {
            // Match not found
            onSubtitleLongClicked(enabled);
        }
    }

    @Override
    public void onSubtitleLongClicked(boolean enabled) {
        String subtitlesOrigCategoryTitle = getContext().getString(R.string.subtitle_category_title);
        String subtitlesAutoCategoryTitle = subtitlesOrigCategoryTitle + " (" + getContext().getString(R.string.autogenerated) + ")";

        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        settingsPresenter.appendSingleButton(UiOptionItem.from(subtitlesOrigCategoryTitle, optionItem -> {
            List<FormatItem> subtitleFormats = getPlayer().getSubtitleFormats();
            List<FormatItem> subtitleOrigFormats = Helpers.filter(subtitleFormats,
                    value -> value.isDefault() || !SubtitleTrack.isAuto(value.getLanguage()));
            reorderSubtitles(subtitleOrigFormats);
            settingsPresenter.appendRadioCategory(subtitlesOrigCategoryTitle,
                    UiOptionItem.from(subtitleOrigFormats,
                            option -> {
                                FormatItem format = UiOptionItem.toFormat(option);
                                enableSubtitleForChannel(!format.isDefault());
                                getPlayer().setFormat(format);
                                mPlayerData.setFormat(format);
                            },
                            getContext().getString(R.string.subtitles_disabled)));
            settingsPresenter.showDialog();
        }));

        settingsPresenter.appendSingleButton(UiOptionItem.from(subtitlesAutoCategoryTitle, optionItem -> {
            List<FormatItem> subtitleFormats = getPlayer().getSubtitleFormats();
            List<FormatItem> subtitleAutoFormats = Helpers.filter(subtitleFormats,
                    value -> value.isDefault() || SubtitleTrack.isAuto(value.getLanguage()));
            reorderSubtitles(subtitleAutoFormats);
            settingsPresenter.appendRadioCategory(subtitlesAutoCategoryTitle,
                    UiOptionItem.from(subtitleAutoFormats,
                            option -> {
                                FormatItem format = UiOptionItem.toFormat(option);
                                enableSubtitleForChannel(!format.isDefault());
                                getPlayer().setFormat(format);
                                mPlayerData.setFormat(format);
                            },
                            getContext().getString(R.string.subtitles_disabled)));
            settingsPresenter.showDialog();
        }));

        settingsPresenter.appendSingleSwitch(AppDialogUtil.createSubtitleChannelOption(getContext(), mPlayerData));

        OptionCategory stylesCategory = AppDialogUtil.createSubtitleStylesCategory(getContext(), mPlayerData);
        settingsPresenter.appendRadioCategory(stylesCategory.title, stylesCategory.options);

        OptionCategory sizeCategory = AppDialogUtil.createSubtitleSizeCategory(getContext(), mPlayerData);
        settingsPresenter.appendRadioCategory(sizeCategory.title, sizeCategory.options);

        OptionCategory positionCategory = AppDialogUtil.createSubtitlePositionCategory(getContext(), mPlayerData);
        settingsPresenter.appendRadioCategory(positionCategory.title, positionCategory.options);

        settingsPresenter.showDialog(subtitlesOrigCategoryTitle, this::setSubtitleButtonState);
    }

    @Override
    public void onPlaylistAddClicked() {
        if (mPlaylistInfos == null) {
            AppDialogUtil.showAddToPlaylistDialog(getContext(), getPlayer().getVideo(),
                    null);
        } else {
            AppDialogUtil.showAddToPlaylistDialog(getContext(), getPlayer().getVideo(),
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

        if (AppDialogPresenter.instance(getContext()).isDialogShown()) {
            // Activate debug infos/show ui after engine restarting (buffering, sound shift, error?).
            getPlayer().showOverlay(true);
            getPlayer().showDebugInfo(mDebugViewEnabled);
            getPlayer().setDebugButtonState(mDebugViewEnabled);
        }
        
        if (mPlayerTweaksData.isScreenOffTimeoutEnabled() || mPlayerTweaksData.isBootScreenOffEnabled()) {
            prepareScreenOff();
            applyScreenOff(PlayerUI.BUTTON_OFF);
            applyScreenOffTimeout(PlayerUI.BUTTON_OFF);
        }
    }

    @Override
    public void onVideoLoaded(Video item) {
        getPlayer().updateEndingTime();
        applySoundOffButtonState();
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
        SearchData.instance(getContext()).setTempBackgroundModeClass(null);

        // Activate debug infos when restoring after PIP.
        getPlayer().showDebugInfo(mDebugViewEnabled);
        getPlayer().setDebugButtonState(mDebugViewEnabled);
        getPlayer().showSubtitles(true);

        // Maybe dialog just closed. Reset timeout just in case.
        enableUiAutoHideTimeout();
        applySoundOffButtonState();
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
        getPlayer().setChannelIcon(null);
        getPlayer().setPlaylistAddButtonState(false);
        getPlayer().setSubtitleButtonState(false);
        getPlayer().setSpeedButtonState(false);
        getPlayer().setChatButtonState(false);
        getPlayer().setButtonState(R.id.action_subscribe, PlayerUI.BUTTON_OFF);
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
        if (mPlayerTweaksData.isRealChannelIconEnabled()) {
            getPlayer().setChannelIcon(metadata.getAuthorImageUrl());
        }
        setPlaylistAddButtonStateCached();
        setSubtitleButtonState();
        getPlayer().setButtonState(R.id.action_rotate, mPlayerData.getVideoRotation() == 0 ? PlayerUI.BUTTON_OFF : PlayerUI.BUTTON_ON);
        getPlayer().setButtonState(R.id.action_subscribe, metadata.isSubscribed() ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
        getPlayer().setButtonState(R.id.action_afr, mPlayerData.isAfrEnabled() ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
    }

    @Override
    public void onSuggestionItemLongClicked(Video item) {
        VideoMenuPresenter.instance(getContext()).showMenu(item, (videoItem, action) -> {
            String title = getContext().getString(R.string.action_playback_queue);
            int id = title.hashCode();

            if (action == VideoMenuCallback.ACTION_REMOVE_FROM_QUEUE) {
                VideoGroup group = VideoGroup.from(videoItem);
                group.setTitle(title);
                group.setId(id);
                getPlayer().removeSuggestions(group);
            } else if (action == VideoMenuCallback.ACTION_ADD_TO_QUEUE || action == VideoMenuCallback.ACTION_PLAY_NEXT) {
                Video newItem = videoItem.copy();
                VideoGroup group = VideoGroup.from(newItem, 0);
                group.setTitle(title);
                group.setId(id);
                newItem.setGroup(group);
                if (action == VideoMenuCallback.ACTION_PLAY_NEXT) {
                    group.setAction(VideoGroup.ACTION_PREPEND);
                }
                getPlayer().updateSuggestions(group);
                getPlayer().setNextTitle(mSuggestionsController.getNext() != null ? mSuggestionsController.getNext().getTitle() : null);
            }
        });
    }

    @Override
    public void onDislikeClicked(boolean dislike) {
        if (!mIsMetadataLoaded) {
            MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);
            getPlayer().setDislikeButtonState(!dislike);
            return;
        }

        if (!YouTubeSignInService.instance().isSigned()) {
            getPlayer().setDislikeButtonState(false);
            MessageHelpers.showMessage(getContext(), R.string.msg_signed_users_only);
            return;
        }

        if (dislike) {
            callMediaItemObservable(mMediaItemService::setDislikeObserve);
        } else {
            callMediaItemObservable(mMediaItemService::removeDislikeObserve);
        }
    }

    @Override
    public void onLikeClicked(boolean like) {
        if (!mIsMetadataLoaded) {
            MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);
            getPlayer().setLikeButtonState(!like);
            return;
        }

        if (!YouTubeSignInService.instance().isSigned()) {
            getPlayer().setLikeButtonState(false);
            MessageHelpers.showMessage(getContext(), R.string.msg_signed_users_only);
            return;
        }

        if (like) {
            callMediaItemObservable(mMediaItemService::setLikeObserve);
        } else {
            callMediaItemObservable(mMediaItemService::removeLikeObserve);
        }
    }

    @Override
    public void onSeekIntervalClicked() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        AppDialogUtil.appendSeekIntervalDialogItems(getContext(), settingsPresenter, mPlayerData, true);

        settingsPresenter.showDialog();
    }

    @Override
    public void onVideoInfoClicked() {
        if (!mIsMetadataLoaded) {
            MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);
            return;
        }

        Video video = getPlayer().getVideo();

        if (video == null) {
            return;
        }

        String description = video.description;

        if (description == null || description.isEmpty()) {
            MessageHelpers.showMessage(getContext(), R.string.description_not_found);
            return;
        }

        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(getContext());

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

        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(getContext());

        int positionSec = Utils.toSec(getPlayer().getPositionMs());
        AppDialogUtil.appendShareLinkDialogItem(getContext(), dialogPresenter, getPlayer().getVideo(), positionSec);
        AppDialogUtil.appendShareQRLinkDialogItem(getContext(), dialogPresenter, getPlayer().getVideo(), positionSec);
        AppDialogUtil.appendShareEmbedLinkDialogItem(getContext(), dialogPresenter, getPlayer().getVideo(), positionSec);

        dialogPresenter.showDialog(getPlayer().getVideo().getTitle());

        //if (video.videoId != null) {
        //    Utils.displayShareVideoDialog(getActivity(), video.videoId, (int)(getController().getPositionMs() / 1_000));
        //} else if (video.channelId != null) {
        //    Utils.displayShareChannelDialog(getActivity(), video.channelId);
        //}
    }

    @Override
    public void onSearchClicked() {
        startTempBackgroundMode(SearchPresenter.class);
        SearchPresenter.instance(getContext()).startSearch(null);
    }

    @Override
    public void onVideoZoomClicked() {
        OptionCategory videoZoomCategory = AppDialogUtil.createVideoZoomCategory(
                getContext(), mPlayerData, () -> {
                    getPlayer().setVideoZoomMode(mPlayerData.getVideoZoomMode());
                    getPlayer().setVideoZoom(mPlayerData.getVideoZoom());
                    getPlayer().showControls(false);
                });

        OptionCategory videoAspectCategory = AppDialogUtil.createVideoAspectCategory(
                getContext(), mPlayerData, () -> getPlayer().setVideoAspectRatio(mPlayerData.getVideoAspectRatio()));

        OptionCategory videoRotateCategory = AppDialogUtil.createVideoRotateCategory(
                getContext(), mPlayerData, () -> getPlayer().setVideoRotation(mPlayerData.getVideoRotation()));

        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());
        settingsPresenter.appendRadioCategory(videoAspectCategory.title, videoAspectCategory.options);
        settingsPresenter.appendRadioCategory(videoZoomCategory.title, videoZoomCategory.options);
        settingsPresenter.appendRadioCategory(videoRotateCategory.title, videoRotateCategory.options);
        settingsPresenter.showDialog(getContext().getString(R.string.video_aspect));
    }

    @Override
    public void onPipClicked() {
        getPlayer().showOverlay(false);
        getPlayer().blockEngine(true);
        //getPlayer().setBackgroundMode(
        //        Helpers.isPictureInPictureSupported(getContext()) ?
        //                PlayerEngine.BACKGROUND_MODE_PIP : PlayerEngine.BACKGROUND_MODE_SOUND
        //);
        getPlayer().finish();
    }

    @Override
    public void onButtonClicked(int buttonId, int buttonState) {
        if (buttonId == R.id.action_rotate) {
            onRotate();
        } else if (buttonId == R.id.action_screen_off || buttonId == R.id.action_screen_off_timeout) {
            prepareScreenOff();
            applyScreenOff(buttonState);
            applyScreenOffTimeout(buttonState);
        } else if (buttonId == R.id.action_subscribe) {
            onSubscribe(buttonState);
        } else if (buttonId == R.id.action_sound_off) {
            applySoundOff(buttonState);
        } else if (buttonId == R.id.action_afr) {
            applyAfr(buttonState);
        } else if (buttonId == R.id.action_repeat) {
            applyRepeatMode(buttonState);
        }
    }

    @Override
    public void onButtonLongClicked(int buttonId, int buttonState) {
        if (buttonId == R.id.action_screen_off || buttonId == R.id.action_screen_off_timeout) {
            showScreenOffDialog();
        } else if (buttonId == R.id.action_subscribe) {
            showNotificationsDialog(buttonState);
        } else if (buttonId == R.id.action_sound_off) {
            showSoundOffDialog();
        } else if (buttonId == R.id.action_afr) {
            AutoFrameRateSettingsPresenter.instance(getContext()).show(() -> applyAfr(mPlayerData.isAfrEnabled() ? PlayerUI.BUTTON_OFF : PlayerUI.BUTTON_ON));
        }
    }

    private void disableUiAutoHideTimeout() {
        Log.d(TAG, "Stopping auto hide ui timer...");
        mHandler.removeCallbacks(mUiAutoHideHandler);
    }

    private void enableUiAutoHideTimeout() {
        Log.d(TAG, "Starting auto hide ui timer...");
        disableUiAutoHideTimeout();
        if (mEngineReady && mPlayerData.getUiHideTimeoutSec() > 0) {
            mHandler.postDelayed(mUiAutoHideHandler, mPlayerData.getUiHideTimeoutSec() * 1_000L);
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

            // Back key cooling
            if (System.currentTimeMillis() - mOverlayHideTimeMs < 1_000) {
                return true;
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

    private boolean handleShortsNavigation(int keyCode) {
        if (getPlayer().isOverlayShown() || getPlayer().getVideo() == null || !getPlayer().getVideo().isShorts || !mPlayerTweaksData.isQuickShortsSkipEnabled()) {
            return false;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            getMainController().onNextClicked();
            return true; // hide ui
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            getMainController().onPreviousClicked();
            return true; // hide ui
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
                YouTubeServiceManager.instance().getMediaItemService().getPlaylistsInfoObserve(videoId)
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

        getPlayer().setSubtitleButtonState(isSubtitleEnabled() && isSubtitleSelected());
    }

    private void startTempBackgroundMode(Class<?> clazz) {
        SearchData searchData = SearchData.instance(getContext());
        if (searchData.isTempBackgroundModeEnabled()) {
            searchData.setTempBackgroundModeClass(clazz);
            onPipClicked();
        }
    }

    private boolean isSubtitleSelected() {
        List<FormatItem> subtitleFormats = getPlayer().getSubtitleFormats();

        if (subtitleFormats == null) {
            return false;
        }

        boolean isSelected = false;

        for (FormatItem subtitle : subtitleFormats) {
            if (subtitle.isSelected() && !subtitle.isDefault()) {
                isSelected = true;
                break;
            }
        }

        return isSelected;
    }

    private boolean isSubtitleEnabled() {
        return !mPlayerData.isSubtitlesPerChannelEnabled() || mPlayerData.isSubtitlesPerChannelEnabled(getChannelId());
    }

    private void enableSubtitleForChannel(boolean enable) {
        if (getPlayer() == null || !mPlayerData.isSubtitlesPerChannelEnabled()) {
            return;
        }

        String channelId = getChannelId();
        if (enable) {
            mPlayerData.enableSubtitlesPerChannel(channelId);
        } else {
            mPlayerData.disableSubtitlesPerChannel(channelId);
        }
    }

    private String getChannelId() {
        return getPlayer().getVideo() != null ? getPlayer().getVideo().channelId : null;
    }

    private void applyScreenOff(int buttonState) {
        if (mPlayerTweaksData.getScreenOffTimeoutSec() == 0) {
            boolean isPartialDimming = mPlayerTweaksData.getScreenOffDimmingPercents() < 100;
            mPlayerTweaksData.enableBootScreenOff(buttonState == PlayerUI.BUTTON_OFF && isPartialDimming);
            if (buttonState == PlayerUI.BUTTON_OFF) {
                ScreensaverManager manager = ((MotherActivity) getActivity()).getScreensaverManager();
                manager.doScreenOff();
                manager.setBlocked(isPartialDimming);
                getPlayer().setButtonState(R.id.action_screen_off, isPartialDimming ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
                getPlayer().setButtonState(R.id.action_screen_off_timeout, isPartialDimming ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
            }
        }
    }

    private void applyScreenOffTimeout(int buttonState) {
        if (mPlayerTweaksData.getScreenOffTimeoutSec() > 0) {
            mPlayerTweaksData.enableScreenOffTimeout(buttonState == PlayerUI.BUTTON_OFF);
            getPlayer().setButtonState(R.id.action_screen_off, buttonState == PlayerUI.BUTTON_OFF ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
            getPlayer().setButtonState(R.id.action_screen_off_timeout, buttonState == PlayerUI.BUTTON_OFF ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
        }
    }

    private void prepareScreenOff() {
        ScreensaverManager manager = ((MotherActivity) getActivity()).getScreensaverManager();

        manager.setBlocked(false);
        manager.disable();
        getPlayer().setButtonState(R.id.action_screen_off, PlayerUI.BUTTON_OFF);
        getPlayer().setButtonState(R.id.action_screen_off_timeout, PlayerUI.BUTTON_OFF);
    }

    private void onRotate() {
        int oldRotation = mPlayerData.getVideoRotation();
        int rotation = oldRotation == 0 ? 90 : oldRotation == 90 ? 180 : oldRotation == 180 ? 270 : 0;
        getPlayer().setVideoRotation(rotation);
        getPlayer().setButtonState(R.id.action_rotate, rotation == 0 ? PlayerUI.BUTTON_OFF : PlayerUI.BUTTON_ON);
        mPlayerData.setVideoRotation(rotation);
    }

    private void onSubscribe(int buttonState) {
        if (!mIsMetadataLoaded) {
            MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);
            return;
        }

        if (!YouTubeSignInService.instance().isSigned()) {
            MessageHelpers.showMessage(getContext(), R.string.msg_signed_users_only);
            return;
        }

        if (buttonState == PlayerUI.BUTTON_OFF) {
            callMediaItemObservable(mMediaItemService::subscribeObserve);
        } else {
            callMediaItemObservable(mMediaItemService::unsubscribeObserve);
        }

        getPlayer().getVideo().isSubscribed = buttonState == PlayerUI.BUTTON_OFF;
        getPlayer().setButtonState(R.id.action_subscribe, buttonState == PlayerUI.BUTTON_OFF ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
    }

    private void applySoundOff(int buttonState) {
        if (buttonState == PlayerUI.BUTTON_OFF) {
            mAudioFormat = getPlayer().getAudioFormat();
            getPlayer().setFormat(FormatItem.NO_AUDIO);
        } else {
            getPlayer().setFormat(mAudioFormat);
        }

        getPlayer().setButtonState(R.id.action_sound_off, buttonState == PlayerUI.BUTTON_OFF ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
    }

    private void applySoundOffButtonState() {
        if (getPlayer().getAudioFormat() != null) {
            getPlayer().setButtonState(R.id.action_sound_off,
                    (getPlayer().getAudioFormat().isDefault() || mPlayerData.getPlayerVolume() == 0) ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
        }
    }

    private void applyAfr(int buttonState) {
        mPlayerData.setAfrEnabled(buttonState == PlayerUI.BUTTON_OFF);
        getController(AutoFrameRateController.class).applyAfr();
        getPlayer().setButtonState(R.id.action_afr, buttonState == PlayerUI.BUTTON_OFF ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
    }

    private void applyRepeatMode(int buttonState) {
        //int nextMode = getNextRepeatMode(buttonState);
        //
        //mPlayerData.setRepeatMode(nextMode);
        //getPlayer().setButtonState(R.id.action_repeat, nextMode);

        OptionCategory category = AppDialogUtil.createPlaybackModeCategory(
                getContext(), mPlayerData, () -> {
                    getPlayer().setButtonState(R.id.action_repeat, mPlayerData.getRepeatMode());
                });

        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());
        settingsPresenter.appendRadioCategory(category.title, category.options);
        settingsPresenter.showDialog();
    }

    private int getNextRepeatMode(int buttonState) {
        int[] modeList = {PlayerEngineConstants.REPEAT_MODE_ALL, PlayerEngineConstants.REPEAT_MODE_ONE, PlayerEngineConstants.REPEAT_MODE_SHUFFLE,
                PlayerEngineConstants.REPEAT_MODE_LIST, PlayerEngineConstants.REPEAT_MODE_PAUSE, PlayerEngineConstants.REPEAT_MODE_CLOSE};
        int nextMode = Utils.getNextState(buttonState, modeList);
        return nextMode;
    }

    private void reorderSubtitles(List<FormatItem> subtitleFormats) {
        if (subtitleFormats == null || subtitleFormats.isEmpty() || subtitleFormats.get(0).equals(mPlayerData.getLastSubtitleFormat())) {
            return;
        }

        // Move last format to the top
        int index = 0;
        int begin = subtitleFormats.get(0).isDefault() ? 1 : 0;
        List<FormatItem> topSubtitles = new ArrayList<>();
        while (index != -1) {
            index = subtitleFormats.indexOf(mPlayerData.getLastSubtitleFormat());
            if (index != -1) {
                topSubtitles.add(subtitleFormats.remove(index));
            }
        }
        subtitleFormats.addAll(subtitleFormats.size() < begin ? 0 : begin, topSubtitles);
    }

    private void showNotificationsDialog(int buttonState) {
        if (getPlayer().getVideo() == null || getPlayer().getVideo().notificationStates == null) {
            return;
        }

        // Can't change notification state while unsubscribed
        if (buttonState == PlayerUI.BUTTON_OFF) {
            onSubscribe(buttonState);
            return;
        }

        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        List<OptionItem> items = new ArrayList<>();

        for (NotificationState item : getPlayer().getVideo().notificationStates) {
            items.add(UiOptionItem.from(item.getTitle(), optionItem -> {
                if (optionItem.isSelected()) {
                    MediaServiceManager.instance().setNotificationState(item);
                }
            }, item.isSelected()));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.header_notifications), items);
        settingsPresenter.showDialog(getContext().getString(R.string.header_notifications));
    }

    private void showScreenOffDialog() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());
        OptionCategory dimmingCategory =
                AppDialogUtil.createPlayerScreenOffDimmingCategory(getContext(), mPlayerTweaksData, () -> {
                    prepareScreenOff();
                    applyScreenOff(PlayerUI.BUTTON_OFF);
                });
        OptionCategory category =
                AppDialogUtil.createPlayerScreenOffTimeoutCategory(getContext(), mPlayerTweaksData, () -> {
                    prepareScreenOff();
                    applyScreenOffTimeout(PlayerUI.BUTTON_OFF);
                });
        settingsPresenter.appendRadioCategory(dimmingCategory.title, dimmingCategory.options);
        settingsPresenter.appendRadioCategory(category.title, category.options);
        settingsPresenter.showDialog(getContext().getString(R.string.action_screen_off));
    }

    private void showSoundOffDialog() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());
        OptionCategory audioVolumeCategory = AppDialogUtil.createAudioVolumeCategory(getContext(), mPlayerData, () -> {
            applySoundOff(mPlayerData.getPlayerVolume() == 0 ? PlayerUI.BUTTON_OFF : PlayerUI.BUTTON_ON);
            getPlayer().setVolume(mPlayerData.getPlayerVolume());
        });
        OptionCategory pitchEffectCategory = AppDialogUtil.createPitchEffectCategory(getContext(), getPlayer(), mPlayerData);
        settingsPresenter.appendCategory(audioVolumeCategory);
        settingsPresenter.appendCategory(pitchEffectCategory);
        settingsPresenter.showDialog(getContext().getString(R.string.player_volume));
    }
}
