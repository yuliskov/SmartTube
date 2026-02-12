package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.mediaserviceinterfaces.data.NotificationState;
import com.liskovsoft.mediaserviceinterfaces.data.PlaylistInfo;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;
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
import com.liskovsoft.smartyoutubetv2.common.misc.ScreensaverManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import com.liskovsoft.youtubeapi.service.YouTubeSignInService;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

import java.util.ArrayList;
import java.util.List;

public class PlayerUIController extends BasePlayerController {
    private static final String TAG = PlayerUIController.class.getSimpleName();
    private static final long SUGGESTIONS_RESET_TIMEOUT_MS = 500;
    private final Handler mHandler;
    private final MediaItemService mMediaItemService;
    private SuggestionsController mSuggestionsController;
    private List<PlaylistInfo> mPlaylistInfos;
    private FormatItem mAudioFormat = FormatItem.AUDIO_HQ_MP4A;
    private boolean mEngineReady;
    private boolean mDebugViewEnabled;
    private boolean mIsMetadataLoaded;
    private long mOverlayHideTimeMs;
    private final Runnable mSuggestionsResetHandler = () -> {
        if (getPlayer() == null) {
            return;
        }
        getPlayer().resetSuggestedPosition();
    };
    private final Runnable mUiAutoHideHandler = () -> {
        if (getPlayer() == null) {
            return;
        }

        // Playing the video and dialog overlay isn't shown
        if (getPlayer().isPlaying() && !getAppDialogPresenter().isDialogShown()) {
            if (getPlayer().isControlsShown()) { // don't hide when suggestions is shown
                getPlayer().showOverlay(false);
                mOverlayHideTimeMs = System.currentTimeMillis();
            }
        } else {
            // in seeking state? doing recheck...
            enableUiAutoHideTimeout();
        }
    };
    private final Runnable mSetSubtitleButtonState = this::setSubtitleButtonState;
    private final Runnable mSetPlaylistAddButtonState = this::setPlaylistAddButtonState;

    public PlayerUIController() {
        mHandler = new Handler(Looper.getMainLooper());

        ServiceManager service = YouTubeServiceManager.instance();
        mMediaItemService = service.getMediaItemService();
    }

    @Override
    public void onInit() {
        mSuggestionsController = getController(SuggestionsController.class);

        if (getPlayer() != null) {
            // Could be set once per activity creation (view layout stuff)
            getPlayer().setResizeMode(getPlayerData().getResizeMode());
            getPlayer().setZoomPercents(getPlayerData().getZoomPercents());
            getPlayer().setAspectRatio(getPlayerData().getAspectRatio());
            getPlayer().setRotationAngle(getPlayerData().getRotationAngle());
        }
    }

    @Override
    public void onNewVideo(Video item) {
        enableUiAutoHideTimeout();

        if (item != null && !item.equals(getVideo())) {
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

        if (getPlayer() == null) {
            return false;
        }

        boolean isHandled = handleBackKey(keyCode) || handleMenuKey(keyCode) ||
                handleConfirmKey(keyCode) || handleStopKey(keyCode) || handleNumKeys(keyCode) ||
                handlePlayPauseKey(keyCode) || handleLeftRightSkip(keyCode) || handleUpDownSkip(keyCode);

        if (isHandled) {
            return true; // don't show UI
        }

        enableUiAutoHideTimeout();

        return false;
    }

    private void onSubtitleClicked(boolean enabled) {
        fitVideoIntoDialog();

        // First run
        if (FormatItem.SUBTITLE_NONE.equals(getPlayerData().getLastSubtitleFormat())) {
            onSubtitleLongClicked();
            return;
        }

        // Only default in the list
        if (getPlayer().getSubtitleFormats() == null || getPlayer().getSubtitleFormats().size() == 1) {
            return;
        }

        FormatItem matchedFormat = null;

        for (FormatItem item : getPlayerData().getLastSubtitleFormats()) {
            if (getPlayer().getSubtitleFormats().contains(item)) {
                matchedFormat = item;
                break;
            }
        }

        // Match found
        if (matchedFormat != null) {
            FormatItem format = enabled ? FormatItem.SUBTITLE_NONE : matchedFormat;
            getPlayer().setFormat(format);
            getPlayerData().setFormat(format);
            getPlayer().setButtonState(R.id.lb_control_closed_captioning, !FormatItem.SUBTITLE_NONE.equals(matchedFormat) && !enabled ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
            enableSubtitleForChannel(!enabled);
        } else {
            // Match not found
            onSubtitleLongClicked();
        }
    }

    private void onSubtitleLongClicked() {
        if (getPlayer() == null) {
            return;
        }

        fitVideoIntoDialog();

        String subtitlesOrigCategoryTitle = getContext().getString(R.string.subtitle_category_title);
        String subtitlesAutoCategoryTitle = subtitlesOrigCategoryTitle + " (" + getContext().getString(R.string.autogenerated) + ")";

        AppDialogPresenter settingsPresenter = getAppDialogPresenter();

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
                                getPlayerData().setFormat(format);
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
                                getPlayerData().setFormat(format);
                            },
                            getContext().getString(R.string.subtitles_disabled)));
            settingsPresenter.showDialog();
        }));

        settingsPresenter.appendSingleSwitch(AppDialogUtil.createSubtitleChannelOption(getContext()));

        OptionCategory stylesCategory = AppDialogUtil.createSubtitleStylesCategory(getContext());
        settingsPresenter.appendRadioCategory(stylesCategory.title, stylesCategory.options);

        OptionCategory sizeCategory = AppDialogUtil.createSubtitleSizeCategory(getContext());
        settingsPresenter.appendRadioCategory(sizeCategory.title, sizeCategory.options);

        OptionCategory positionCategory = AppDialogUtil.createSubtitlePositionCategory(getContext());
        settingsPresenter.appendRadioCategory(positionCategory.title, positionCategory.options);

        settingsPresenter.showDialog(subtitlesOrigCategoryTitle, mSetSubtitleButtonState);
    }

    private void onPlaylistAddClicked() {
        fitVideoIntoDialog();

        if (mPlaylistInfos == null) {
            AppDialogUtil.showAddToPlaylistDialog(getContext(), getVideo(),
                    null);
        } else {
            AppDialogUtil.showAddToPlaylistDialog(getContext(), getVideo(),
                    null, mPlaylistInfos, mSetPlaylistAddButtonState);
        }
    }

    private void onDebugInfoClicked(boolean enabled) {
        mDebugViewEnabled = !enabled;
        getPlayer().showDebugInfo(mDebugViewEnabled);
        getPlayer().setButtonState(R.id.action_video_stats, mDebugViewEnabled ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
    }

    @Override
    public void onEngineInitialized() {
        mEngineReady = true;

        if (isEmbedPlayer()) {
            return;
        }

        if (getAppDialogPresenter().isDialogShown()) {
            // Activate debug infos/show ui after engine restarting (buffering, sound shift, error?).
            getPlayer().showOverlay(true);
            getPlayer().showDebugInfo(mDebugViewEnabled);
            getPlayer().setButtonState(R.id.action_video_stats, mDebugViewEnabled ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
        }
        
        if (getPlayerTweaksData().isScreenOffTimeoutEnabled() || getPlayerTweaksData().isBootScreenOffEnabled()) {
            prepareScreenOff();
            applyScreenOff(PlayerUI.BUTTON_OFF);
            applyScreenOffTimeout(PlayerUI.BUTTON_OFF);
        }
    }

    @Override
    public void onVideoLoaded(Video item) {
        if (getPlayer() == null) {
            return;
        }

        getPlayer().updateEndingTime();
        applySoundOffButtonState();
    }

    @Override
    public void onSeekEnd() {
        if (getPlayer() == null) {
            return;
        }

        getPlayer().updateEndingTime();
    }

    @Override
    public void onViewResumed() {
        if (getPlayer() == null) {
            return;
        }

        // Reset temp mode.
        getSearchData().setTempBackgroundModeClass(null);

        // Activate debug infos when restoring after PIP.
        getPlayer().showDebugInfo(mDebugViewEnabled);
        getPlayer().setButtonState(R.id.action_video_stats, mDebugViewEnabled ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
        getPlayer().showSubtitles(true);

        // Maybe dialog just closed. Reset timeout just in case.
        enableUiAutoHideTimeout();
        applySoundOffButtonState();
    }

    @Override
    public void onViewPaused() {
        if (getPlayer() != null && getPlayer().isInPIPMode()) {
            // UI couldn't be properly displayed in PIP mode
            getPlayer().showOverlay(false);
            getPlayer().showDebugInfo(false);
            getPlayer().setButtonState(R.id.action_video_stats, PlayerUI.BUTTON_OFF);
            getPlayer().showSubtitles(false);
        }
    }

    private void resetButtonStates() {
        if (getPlayer() == null) {
            return;
        }

        getPlayer().setButtonState(R.id.action_thumbs_up, PlayerUI.BUTTON_OFF);
        getPlayer().setButtonState(R.id.action_thumbs_down, PlayerUI.BUTTON_OFF);
        getPlayer().setChannelIcon(null);
        getPlayer().setButtonState(R.id.action_playlist_add, PlayerUI.BUTTON_OFF);
        getPlayer().setButtonState(R.id.lb_control_closed_captioning, PlayerUI.BUTTON_OFF);
        getPlayer().setButtonState(R.id.action_video_speed, PlayerUI.BUTTON_OFF);
        getPlayer().setButtonState(R.id.action_chat, PlayerUI.BUTTON_OFF);
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
        if (getPlayerData().getSeekPreviewMode() != PlayerData.SEEK_PREVIEW_NONE) {
            getPlayer().loadStoryboard();
        }
        getPlayer().setButtonState(R.id.action_thumbs_up, metadata.getLikeStatus() == MediaItemMetadata.LIKE_STATUS_LIKE ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
        getPlayer().setButtonState(R.id.action_thumbs_down, metadata.getLikeStatus() == MediaItemMetadata.LIKE_STATUS_DISLIKE ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
        if (getPlayerTweaksData().isRealChannelIconEnabled()) {
            getPlayer().setChannelIcon(metadata.getAuthorImageUrl());
        }
        setPlaylistAddButtonStateCached();
        setSubtitleButtonState();
        getPlayer().setButtonState(R.id.action_rotate, getPlayerData().getRotationAngle() == 0 ? PlayerUI.BUTTON_OFF : PlayerUI.BUTTON_ON);
        getPlayer().setButtonState(R.id.action_subscribe, metadata.isSubscribed() ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
        getPlayer().setButtonState(R.id.action_afr, getPlayerData().isAfrEnabled() ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
    }

    @Override
    public void onSuggestionItemLongClicked(Video item) {
        VideoMenuPresenter.instance(getContext()).showMenu(item, (videoItem, action) -> {
            if (getPlayer() == null || item.getGroup() == null)
                return;

            if (action == VideoMenuCallback.ACTION_REMOVE_FROM_QUEUE
                    || action == VideoMenuCallback.ACTION_REMOVE_FROM_PLAYLIST
                    || action == VideoMenuCallback.ACTION_REMOVE) {
                int id = item.getGroup().getId();
                VideoGroup group = VideoGroup.from(videoItem);
                group.setId(id);
                getPlayer().removeSuggestions(group);
            } else if (action == VideoMenuCallback.ACTION_ADD_TO_QUEUE || action == VideoMenuCallback.ACTION_PLAY_NEXT) {
                String title = getContext().getString(R.string.action_playback_queue);
                int id = title.hashCode();
                Video newItem = videoItem.copy();
                VideoGroup group = VideoGroup.from(newItem, 0);
                group.setTitle(title);
                group.setId(id);
                group.setType(MediaGroup.TYPE_PLAYBACK_QUEUE);
                newItem.setGroup(group);
                if (action == VideoMenuCallback.ACTION_PLAY_NEXT) {
                    group.setAction(VideoGroup.ACTION_PREPEND);
                }
                getPlayer().updateSuggestions(group);
                getPlayer().setNextTitle(mSuggestionsController.getNext());
            }
        });
    }

    private void onDislikeClicked(boolean dislike) {
        if (getPlayer() == null)
            return;

        getPlayer().setButtonState(R.id.action_thumbs_down, !dislike ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);

        if (!mIsMetadataLoaded) {
            MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);
            return;
        }

        if (!YouTubeSignInService.instance().isSigned()) {
            getPlayer().setButtonState(R.id.action_thumbs_down, PlayerUI.BUTTON_OFF);
            MessageHelpers.showMessage(getContext(), R.string.msg_signed_users_only);
            return;
        }

        if (!dislike) {
            callMediaItemObservable(mMediaItemService::setDislikeObserve);
        } else {
            callMediaItemObservable(mMediaItemService::removeDislikeObserve);
        }
    }

    private void onLikeClicked(boolean like) {
        getPlayer().setButtonState(R.id.action_thumbs_up, !like ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);

        if (!mIsMetadataLoaded) {
            MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);
            return;
        }

        if (!YouTubeSignInService.instance().isSigned()) {
            getPlayer().setButtonState(R.id.action_thumbs_up, PlayerUI.BUTTON_OFF);
            MessageHelpers.showMessage(getContext(), R.string.msg_signed_users_only);
            return;
        }

        if (!like) {
            callMediaItemObservable(mMediaItemService::setLikeObserve);
        } else {
            callMediaItemObservable(mMediaItemService::removeLikeObserve);
        }
    }

    private void onSeekInterval() {
        fitVideoIntoDialog();

        AppDialogPresenter settingsPresenter = getAppDialogPresenter();

        AppDialogUtil.appendSeekIntervalDialogItems(getContext(), settingsPresenter, getPlayerData(), true);

        settingsPresenter.showDialog();
    }

    private void onVideoInfoClicked() {
        fitVideoIntoDialog();

        if (!mIsMetadataLoaded) {
            MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);
            return;
        }

        Video video = getVideo();

        if (video == null) {
            return;
        }

        String description = video.description;

        if (description == null || description.isEmpty()) {
            MessageHelpers.showMessage(getContext(), R.string.description_not_found);
            return;
        }

        AppDialogPresenter dialogPresenter = getAppDialogPresenter();

        String title = String.format("%s - %s", video.getTitleFull(), video.getAuthor());

        dialogPresenter.appendLongTextCategory(title, UiOptionItem.from(description));

        dialogPresenter.showDialog(title);
    }

    private void onShareLink() {
        fitVideoIntoDialog();

        Video video = getVideo();

        if (video == null) {
            return;
        }

        AppDialogPresenter dialogPresenter = getAppDialogPresenter();

        int positionSec = Utils.toSec(getPlayer().getPositionMs());
        AppDialogUtil.appendShareLinkDialogItem(getContext(), dialogPresenter, getVideo(), positionSec);
        AppDialogUtil.appendShareQRLinkDialogItem(getContext(), dialogPresenter, getVideo(), positionSec);
        AppDialogUtil.appendShareEmbedLinkDialogItem(getContext(), dialogPresenter, getVideo(), positionSec);

        dialogPresenter.showDialog(getVideo().getTitle());
    }

    private void onSearchClicked() {
        startTempBackgroundMode(SearchPresenter.class);
        SearchPresenter.instance(getContext()).startSearch(null);
    }
    
    private void onVideoZoom() {
        OptionCategory videoZoomCategory = AppDialogUtil.createVideoZoomCategory(
                getContext(), () -> {
                    getPlayer().setResizeMode(getPlayerData().getResizeMode());
                    getPlayer().setZoomPercents(getPlayerData().getZoomPercents());
                    getPlayer().showControls(false);
                });

        OptionCategory videoAspectCategory = AppDialogUtil.createVideoAspectCategory(
                getContext(), getPlayerData(), () -> getPlayer().setAspectRatio(getPlayerData().getAspectRatio()));

        OptionCategory videoRotateCategory = AppDialogUtil.createVideoRotateCategory(
                getContext(), getPlayerData(), () -> getPlayer().setRotationAngle(getPlayerData().getRotationAngle()));

        AppDialogPresenter settingsPresenter = getAppDialogPresenter();
        settingsPresenter.appendRadioCategory(videoAspectCategory.title, videoAspectCategory.options);
        settingsPresenter.appendRadioCategory(videoZoomCategory.title, videoZoomCategory.options);
        settingsPresenter.appendRadioCategory(videoRotateCategory.title, videoRotateCategory.options);
        settingsPresenter.showDialog(getContext().getString(R.string.video_aspect));
    }

    private void onPipClicked() {
        getPlayer().showOverlay(false);
        getPlayer().blockEngine(true);
        getPlayer().finish();
    }

    @Override
    public void onButtonClicked(int buttonId, int buttonState) {
        if (buttonId == R.id.action_rotate) {
            onRotate();
        } else if (buttonId == R.id.action_flip) {
            onFlip();
        } else if (buttonId == R.id.action_screen_dimming) {
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
        } else if (buttonId == R.id.action_channel) {
            openChannel();
        } else if (buttonId == R.id.action_playback_queue) {
            AppDialogUtil.showPlaybackQueueDialog(getContext(), item -> getMainController().onNewVideo(item));
        } else if (buttonId == R.id.action_video_zoom) {
            onVideoZoom();
        } else if (buttonId == R.id.action_seek_interval) {
            onSeekInterval();
        } else if (buttonId == R.id.action_share) {
            onShareLink();
        } else if (buttonId == R.id.action_info) {
            onVideoInfoClicked();
        } else if (buttonId == R.id.action_pip) {
            onPipClicked();
        } else if (buttonId == R.id.action_search) {
            onSearchClicked();
        } else if (buttonId == R.id.action_video_stats) {
            onDebugInfoClicked(buttonState == PlayerUI.BUTTON_ON);
        } else if (buttonId == R.id.action_playlist_add) {
            onPlaylistAddClicked();
        } else if (buttonId == R.id.lb_control_closed_captioning) {
            onSubtitleClicked(buttonState == PlayerUI.BUTTON_ON);
        } else if (buttonId == R.id.action_thumbs_down) {
            onDislikeClicked(buttonState == PlayerUI.BUTTON_ON);
        } else if (buttonId == R.id.action_thumbs_up) {
            onLikeClicked(buttonState == PlayerUI.BUTTON_ON);
        }
    }

    @Override
    public void onButtonLongClicked(int buttonId, int buttonState) {
        if (buttonId == R.id.action_screen_dimming) {
            showScreenOffDialog();
        } else if (buttonId == R.id.action_subscribe || buttonId == R.id.action_channel) {
            showNotificationsDialog(buttonState);
        } else if (buttonId == R.id.action_sound_off) {
            showSoundOffDialog();
        } else if (buttonId == R.id.action_afr) {
            AutoFrameRateSettingsPresenter.instance(getContext()).show(() -> applyAfr(getPlayerData().isAfrEnabled() ? PlayerUI.BUTTON_OFF : PlayerUI.BUTTON_ON));
        } else if (buttonId == R.id.action_repeat) {
            showPlaybackModeDialog(buttonState);
        } else if (buttonId == R.id.lb_control_closed_captioning) {
            onSubtitleLongClicked();
        }
    }

    private void disableUiAutoHideTimeout() {
        Log.d(TAG, "Stopping auto hide ui timer...");
        mHandler.removeCallbacks(mUiAutoHideHandler);
    }

    private void enableUiAutoHideTimeout() {
        Log.d(TAG, "Starting auto hide ui timer...");
        disableUiAutoHideTimeout();
        if (mEngineReady && getPlayerData().getUiHideTimeoutSec() > 0) {
            mHandler.postDelayed(mUiAutoHideHandler, getPlayerData().getUiHideTimeoutSec() * 1_000L);
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
        Video video = getVideo();

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

            // Close unplayable videos with single back click
            // Cause the background playback bugs if not to check for upcoming or unplayable!!!
            // To reproduce the bug:
            // 1) Set bg black to "Only audio when pressing HOME"
            // 2) Enable "keep finished activities"
            // 3) Close the video when it fully finished and ready to skip to the next
            if (getVideo() != null && getPlayer() != null &&
                    (getVideo().isUnplayable || getVideo().isUpcoming) && getPlayer().isControlsShown()) {
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
        if (getPlayer() == null) {
            return false;
        }

        boolean controlsShown = getPlayer().isOverlayShown();

        if (KeyHelpers.isConfirmKey(keyCode) && !controlsShown) {
            switch (getPlayerData().getOKButtonBehavior()) {
                case PlayerData.OK_ONLY_UI:
                    getPlayer().showOverlay(true);
                    return true; // don't show ui
                case PlayerData.OK_UI_AND_PAUSE:
                    // NOP
                    break;
                case PlayerData.OK_ONLY_PAUSE:
                    getPlayer().setPlayWhenReady(!getPlayer().getPlayWhenReady());
                    return true; // don't show ui
                //case PlayerData.OK_TOGGLE_SPEED:
                //    getMainController().onButtonClicked(R.id.action_video_speed,
                //            getPlayer().getButtonState(R.id.action_video_speed) == PlayerUI.BUTTON_ON ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
                //    float speed = getPlayerData().getSpeed();
                //    MessageHelpers.showMessage(getContext(), String.format("%sx", speed));
                //    return true;
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
        if (getPlayerData().isNumberKeySeekEnabled() && keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            if (getPlayer() != null && getPlayer().getDurationMs() > 0) {
                float seekPercent = (keyCode - KeyEvent.KEYCODE_0) / 10f;
                getPlayer().setPositionMs((long)(getPlayer().getDurationMs() * seekPercent));
            }
        }

        return false;
    }

    private boolean handlePlayPauseKey(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && getPlayer() != null) {
            getPlayer().setPlayWhenReady(!getPlayer().getPlayWhenReady());
            enableUiAutoHideTimeout(); // TODO: move out somehow
            return true;
        }

        return false;
    }

    private boolean handleLeftRightSkip(int keyCode) {
        if (getPlayer() == null || getPlayer().isOverlayShown() || getVideo() == null ||
                (getVideo().belongsToShortsGroup() && !getPlayerTweaksData().isQuickSkipShortsEnabled() ||
                (!getVideo().belongsToShortsGroup() && !getPlayerTweaksData().isQuickSkipVideosEnabled()))) {
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

    private boolean handleUpDownSkip(int keyCode) {
        if (getPlayer() == null || getPlayer().isOverlayShown() || getVideo() == null ||
                (getVideo().belongsToShortsGroup() && !getPlayerTweaksData().isQuickSkipShortsAltEnabled() ||
                        (!getVideo().belongsToShortsGroup() && !getPlayerTweaksData().isQuickSkipVideosAltEnabled()))) {
            return false;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            getMainController().onNextClicked();
            return true; // hide ui
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            getMainController().onPreviousClicked();
            return true; // hide ui
        }

        return false;
    }

    private interface MediaItemObservable {
        Observable<Void> call(MediaItem item);
    }

    private void setPlaylistAddButtonStateCached() {
        if (getVideo() == null) {
            return;
        }

        String videoId = getVideo().videoId;
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

        getPlayer().setButtonState(R.id.action_playlist_add, isSelected ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
    }

    private void setSubtitleButtonState() {
        if (getPlayer() == null) {
            return;
        }

        getPlayer().setButtonState(R.id.lb_control_closed_captioning, isSubtitleEnabled() && isSubtitleSelected() ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
    }

    private void startTempBackgroundMode(Class<?> clazz) {
        SearchData searchData = getSearchData();
        if (searchData.isTempBackgroundModeEnabled()) {
            searchData.setTempBackgroundModeClass(clazz);
            onPipClicked();
        }
    }

    private boolean isSubtitleSelected() {
        if (getPlayer() == null) {
            return false;
        }

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
        return !getPlayerData().isSubtitlesPerChannelEnabled() || getPlayerData().isSubtitlesPerChannelEnabled(getChannelId());
    }

    private void enableSubtitleForChannel(boolean enable) {
        if (getPlayer() == null || !getPlayerData().isSubtitlesPerChannelEnabled()) {
            return;
        }

        String channelId = getChannelId();
        if (enable) {
            getPlayerData().enableSubtitlesPerChannel(channelId);
        } else {
            getPlayerData().disableSubtitlesPerChannel(channelId);
        }
    }

    private String getChannelId() {
        return getVideo() != null ? getVideo().channelId : null;
    }

    private void applyScreenOff(int buttonState) {
        if (getPlayer() == null) {
            return;
        }

        ScreensaverManager manager = getScreensaverManager();

        if (manager == null) {
            return;
        }

        if (getPlayerTweaksData().getScreenOffTimeoutSec() == 0) {
            boolean isPartialDimming = getPlayerTweaksData().getScreenOffDimmingPercents() < 100;
            getPlayerTweaksData().setBootScreenOffEnabled(buttonState == PlayerUI.BUTTON_OFF && isPartialDimming);
            if (buttonState == PlayerUI.BUTTON_OFF) {
                manager.doScreenOff();
                manager.setBlocked(isPartialDimming);
                getPlayer().setButtonState(R.id.action_screen_dimming, isPartialDimming ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
            }
        }
    }

    private void applyScreenOffTimeout(int buttonState) {
        if (getPlayer() == null) {
            return;
        }

        if (getPlayerTweaksData().getScreenOffTimeoutSec() > 0) {
            getPlayerTweaksData().setScreenOffTimeoutEnabled(buttonState == PlayerUI.BUTTON_OFF);
            getPlayer().setButtonState(R.id.action_screen_dimming, buttonState == PlayerUI.BUTTON_OFF ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
        }
    }

    private void prepareScreenOff() {
        if (getPlayer() == null) {
            return;
        }

        ScreensaverManager manager = getScreensaverManager();

        if (manager == null) {
            return;
        }

        manager.setBlocked(false);
        manager.disable();
        getPlayer().setButtonState(R.id.action_screen_dimming, PlayerUI.BUTTON_OFF);
    }

    private void onRotate() {
        if (getPlayer() == null) {
            return;
        }

        int oldRotation = getPlayerData().getRotationAngle();
        int rotation = oldRotation == 0 ? 90 : oldRotation == 90 ? 180 : oldRotation == 180 ? 270 : 0;
        getPlayer().setRotationAngle(rotation);
        getPlayer().setButtonState(R.id.action_rotate, rotation == 0 ? PlayerUI.BUTTON_OFF : PlayerUI.BUTTON_ON);
        getPlayerData().setRotationAngle(rotation);
    }

    private void onFlip() {
        if (getPlayer() == null) {
            return;
        }

        boolean flipEnabled = getPlayerData().isVideoFlipEnabled();
        boolean newFlipEnabled = !flipEnabled;
        getPlayer().setVideoFlipEnabled(newFlipEnabled);
        getPlayer().setButtonState(R.id.action_flip, newFlipEnabled ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
        getPlayerData().setVideoFlipEnabled(newFlipEnabled);
    }

    private void onSubscribe(int buttonState) {
        if (getPlayer() == null || getVideo() == null) {
            return;
        }

        if (!mIsMetadataLoaded) {
            MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);
            return;
        }

        if (buttonState == PlayerUI.BUTTON_OFF) {
            callMediaItemObservable(mMediaItemService::subscribeObserve);
        } else {
            callMediaItemObservable(mMediaItemService::unsubscribeObserve);
        }

        getVideo().isSubscribed = buttonState == PlayerUI.BUTTON_OFF;
        getPlayer().setButtonState(R.id.action_subscribe, buttonState == PlayerUI.BUTTON_OFF ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
    }

    private void applySoundOff(int buttonState) {
        if (getPlayer() == null) {
            return;
        }

        if (buttonState == PlayerUI.BUTTON_OFF) {
            mAudioFormat = getPlayer().getAudioFormat();
            getPlayer().setFormat(FormatItem.NO_AUDIO);
            getPlayerData().setFormat(FormatItem.NO_AUDIO);
        } else {
            getPlayer().setFormat(mAudioFormat);
            getPlayerData().setFormat(mAudioFormat);
        }

        getPlayer().setButtonState(R.id.action_sound_off, buttonState == PlayerUI.BUTTON_OFF ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
    }

    private void applySoundOffButtonState() {
        if (getPlayer() != null && getPlayer().getAudioFormat() != null) {
            getPlayer().setButtonState(R.id.action_sound_off,
                    (getPlayer().getAudioFormat().isDefault() || getPlayerData().getPlayerVolume() == 0) ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
        }
    }

    private void applyAfr(int buttonState) {
        getPlayerData().setAfrEnabled(buttonState == PlayerUI.BUTTON_OFF);
        getController(AutoFrameRateController.class).applyAfr();
        getPlayer().setButtonState(R.id.action_afr, buttonState == PlayerUI.BUTTON_OFF ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
    }

    private void applyRepeatMode(int buttonState) {
        int nextMode = getNextRepeatMode(buttonState);

        getPlayerData().setPlaybackMode(nextMode);
        getPlayer().setButtonState(R.id.action_repeat, nextMode);
    }

    private void showPlaybackModeDialog(int buttonState) {
        OptionCategory category = AppDialogUtil.createPlaybackModeCategory(
                getContext(), () -> {
                    getPlayer().setButtonState(R.id.action_repeat, getPlayerData().getPlaybackMode());
                });

        AppDialogPresenter settingsPresenter = getAppDialogPresenter();
        settingsPresenter.appendRadioCategory(category.title, category.options);
        settingsPresenter.showDialog();
    }

    private int getNextRepeatMode(int buttonState) {
        Integer[] modeList = {PlayerConstants.PLAYBACK_MODE_ALL, PlayerConstants.PLAYBACK_MODE_ONE, PlayerConstants.PLAYBACK_MODE_SHUFFLE,
                PlayerConstants.PLAYBACK_MODE_LIST, PlayerConstants.PLAYBACK_MODE_REVERSE_LIST, PlayerConstants.PLAYBACK_MODE_PAUSE, PlayerConstants.PLAYBACK_MODE_CLOSE};
        int nextMode = Helpers.getNextValue(modeList, buttonState);
        return nextMode;
    }

    private void reorderSubtitles(List<FormatItem> subtitleFormats) {
        if (subtitleFormats == null || subtitleFormats.isEmpty()) {
            return;
        }

        // Move last format to the top
        int begin = subtitleFormats.get(0).isDefault() ? 1 : 0;
        List<FormatItem> topSubtitles = new ArrayList<>();
        for (FormatItem item : getPlayerData().getLastSubtitleFormats()) {
            if (item == null || item.getLanguage() == null) { // skip empty formats
                continue;
            }
            int index = 0;
            while (index != -1) {
                index = subtitleFormats.indexOf(item);
                if (index != -1) {
                    topSubtitles.add(subtitleFormats.remove(index));
                }
            }
        }
        subtitleFormats.addAll(subtitleFormats.size() < begin ? 0 : begin, topSubtitles);
    }

    private void showNotificationsDialog(int buttonState) {
        if (getVideo() == null || getVideo().notificationStates == null) {
            return;
        }

        AppDialogPresenter settingsPresenter = getAppDialogPresenter();

        List<OptionItem> items = new ArrayList<>();

        for (NotificationState item : getVideo().notificationStates) {
            items.add(UiOptionItem.from(item.getTitle(), optionItem -> {
                if (optionItem.isSelected()) {
                    MediaServiceManager.instance().setNotificationState(item, error -> MessageHelpers.showMessage(getContext(), error.getLocalizedMessage()));
                    getVideo().isSubscribed = true;
                    getPlayer().setButtonState(R.id.action_subscribe, PlayerUI.BUTTON_ON);
                }
            }, item.isSelected()));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.header_notifications), items);
        settingsPresenter.showDialog(getContext().getString(R.string.header_notifications));
    }

    private void showScreenOffDialog() {
        AppDialogPresenter settingsPresenter = getAppDialogPresenter();
        OptionCategory dimmingCategory =
                AppDialogUtil.createPlayerScreenOffDimmingCategory(getContext(), () -> {
                    prepareScreenOff();
                    applyScreenOff(PlayerUI.BUTTON_OFF);
                });
        OptionCategory category =
                AppDialogUtil.createPlayerScreenOffTimeoutCategory(getContext(), () -> {
                    prepareScreenOff();
                    applyScreenOffTimeout(PlayerUI.BUTTON_OFF);
                });
        settingsPresenter.appendRadioCategory(dimmingCategory.title, dimmingCategory.options);
        settingsPresenter.appendRadioCategory(category.title, category.options);
        settingsPresenter.showDialog(getContext().getString(R.string.screen_dimming));
    }

    private void showSoundOffDialog() {
        AppDialogPresenter settingsPresenter = getAppDialogPresenter();
        OptionCategory audioVolumeCategory = AppDialogUtil.createAudioVolumeCategory(getContext(), () -> {
            applySoundOff(getPlayerData().getPlayerVolume() == 0 ? PlayerUI.BUTTON_OFF : PlayerUI.BUTTON_ON);
            getPlayer().setVolume(getPlayerData().getPlayerVolume());
        });
        OptionCategory pitchEffectCategory = AppDialogUtil.createPitchEffectCategory(getContext());
        settingsPresenter.appendCategory(audioVolumeCategory);
        settingsPresenter.appendCategory(pitchEffectCategory);
        settingsPresenter.showDialog(getContext().getString(R.string.player_volume));
    }

    private void openChannel() {
        startTempBackgroundMode(ChannelPresenter.class);
        ChannelPresenter.instance(getContext()).openChannel(getVideo());
    }
}
