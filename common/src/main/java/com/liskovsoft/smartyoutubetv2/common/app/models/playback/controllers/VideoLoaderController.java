package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import android.annotation.SuppressLint;
import android.util.Pair;

import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.mediaserviceinterfaces.data.MediaFormat;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SimpleMediaItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.VideoActionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import io.reactivex.disposables.Disposable;

import java.util.Collections;
import java.util.List;

public class VideoLoaderController extends BasePlayerController {
    private static final String TAG = VideoLoaderController.class.getSimpleName();
    private static final long STREAM_END_THRESHOLD_MS = 180_000;
    private static final long BUFFERING_THRESHOLD_MS = 3_000;
    private static final long BUFFERING_WINDOW_MS = 60_000;
    private static final long BUFFERING_RECURRENCE_COUNT = 5;
    private static final long BUFFERING_CONTINUATION_MS = 10_000;
    private final Playlist mPlaylist;
    private Video mPendingVideo;
    private int mLastErrorType = -1;
    private SuggestionsController mSuggestionsController;
    private long mSleepTimerStartMs;
    private Disposable mFormatInfoAction;
    private Disposable mMpdStreamAction;
    private final Runnable mReloadVideo = () -> {
        getMainController().onNewVideo(getVideo());
    };
    private final Runnable mLoadNext = this::loadNext;
    private final Runnable mMetadataSync = () -> {
        if (getPlayer() != null) {
            waitMetadataSync(getVideo(), false);
        }
    };
    private final Runnable mRestartEngine = () -> {
        if (getPlayer() != null) {
            getPlayer().restartEngine(); // properly save position of the current track
        }
    };
    private final Runnable mOnLongBuffering = this::updateBufferingCountIfNeeded;

    private final Runnable mRebootApp = () -> {
        Video video = getVideo();
        if (getPlayer() != null) {
            Utils.restartTheApp(getContext(), video, getPlayer().getPositionMs());
        }
    };
    private final Runnable mOnApplyPlaybackMode = () -> {
        if (getPlayer() != null && getPlayer().getPositionMs() >= getPlayer().getDurationMs()) {
            applyPlaybackMode(getPlaybackMode());
        }
    };
    private Pair<Integer, Long> mBufferingCount;

    public VideoLoaderController() {
        mPlaylist = Playlist.instance();
    }

    @Override
    public void onInit() {
        mSuggestionsController = getController(SuggestionsController.class);
        mSleepTimerStartMs = System.currentTimeMillis();
    }

    @Override
    public void onNewVideo(Video item) {
        if (item == null) {
            return;
        }

        if (!item.fromQueue && !item.belongsToPlaybackQueue()) {
            mPlaylist.add(item);
        } else {
            item.fromQueue = false;
        }

        if (getPlayer() != null && getPlayer().isEngineInitialized()) { // player is initialized
            // Fix improperly resized video after exit from PIP (Device Formuler Z8 Pro)
            loadVideo(item); // force play immediately even the same video
        } else {
            mPendingVideo = item;
        }
    }

    @Override
    public void onBuffering() {
        Utils.postDelayed(mOnLongBuffering, BUFFERING_THRESHOLD_MS);
    }

    @Override
    public void onSeekEnd() {
        // Reset buffering stats
        mBufferingCount = null;
    }

    private void onLongBuffering() {
        if (getPlayer() == null || getVideo() == null) {
            return;
        }

        // Stream end check (hangs on buffering)
        if (getPlayerTweaksData().isHighBitrateFormatsEnabled()) {
            getPlayerTweaksData().setHighBitrateFormatsEnabled(false); // Response code: 429
            reloadVideo();
        } else if ((!getVideo().isLive || getVideo().isLiveEnd)
                && getPlayer().getDurationMs() - getPlayer().getPositionMs() < STREAM_END_THRESHOLD_MS) {
            getMainController().onPlayEnd();
        } else if (!getVideo().isLive && !getVideo().isLiveEnd && !getPlayerTweaksData().isNetworkErrorFixingDisabled()) {
            MessageHelpers.showLongMessage(getContext(), R.string.playback_buffering_fix);
            YouTubeServiceManager.instance().invalidateCache();
            // Faster source is different among devices. Try them one by one.
            switchNextEngine();
            restartEngine();
        }
    }

    @Override
    public void onEngineInitialized() {
        if (getPlayer() == null) {
            return;
        }
        
        loadVideo(Helpers.firstNonNull(mPendingVideo, getVideo()));
        getPlayer().setButtonState(R.id.action_repeat, getPlayerData().getPlaybackMode());
        mSleepTimerStartMs = System.currentTimeMillis();
        mPendingVideo = null;
    }

    @Override
    public void onEngineReleased() {
        disposeActions();
    }

    @Override
    public void onEngineError(int type, int rendererIndex, Throwable error) {
        Log.e(TAG, "Player error occurred: %s. Trying to fix…", type);

        mLastErrorType = type;
        runEngineErrorAction(type, rendererIndex, error);
    }

    @Override
    public void onVideoLoaded(Video video) {
        if (getPlayer() == null) {
            return;
        }

        mLastErrorType = -1;
        getPlayer().setButtonState(R.id.action_repeat, video.finishOnEnded ? PlayerConstants.PLAYBACK_MODE_CLOSE : getPlayerData().getPlaybackMode());
        // Can't set title at this point
        //checkSleepTimer();
    }

    @Override
    public boolean onPreviousClicked() {
        loadPrevious();

        return true;
    }

    @Override
    public boolean onNextClicked() {
        if (getGeneralData().isChildModeEnabled()) {
            onPlayEnd();
        } else {
            loadNext();
        }

        return true;
    }

    public void loadPrevious() {
        if (getPlayer() == null) {
            return;
        }

        openVideoInt(mSuggestionsController.getPrevious());

        if (getPlayerTweaksData().isPlayerUiOnNextEnabled()) {
            getPlayer().showOverlay(true);
        }
    }

    public void loadNext() {
        if (getPlayer() == null || getVideo() == null) {
            return;
        }

        Video next = mSuggestionsController.getNext();

        if (next != null) {
            next.isShuffled = getVideo().isShuffled;
            openVideoInt(next);
        } else {
            waitMetadataSync(getVideo(), true);
        }

        if (getPlayerTweaksData().isPlayerUiOnNextEnabled()) {
            getPlayer().showOverlay(true);
        }
    }

    @Override
    public void onPlayEnd() {
        if (getPlayer() == null) {
            return;
        }

        // Stop the playback if the user is browsing options or reading comments
        int playbackMode = getPlaybackMode();
        if (getAppDialogPresenter().isDialogShown() && !getAppDialogPresenter().isOverlay() && playbackMode != PlayerConstants.PLAYBACK_MODE_ONE) {
            getAppDialogPresenter().setOnFinish(mOnApplyPlaybackMode);
        } else {
            applyPlaybackMode(playbackMode);
        }
    }

    @Override
    public void onSuggestionItemClicked(Video item) {
        openVideoInt(item);

        if (getPlayer() != null)
            getPlayer().showControls(false);
    }

    @Override
    public boolean onKeyDown(int keyCode) {
        if (getPlayer() == null) {
            return false;
        }

        mSleepTimerStartMs = System.currentTimeMillis();

        // Remove error msg if needed
        if (getPlayerData().isSleepTimerEnabled()) {
            getPlayer().setVideo(getVideo());
        }

        Utils.removeCallbacks(mRestartEngine, mRebootApp);

        return false;
    }

    @Override
    public void onTickle() {
        checkSleepTimer();
    }

    private void checkSleepTimer() {
        if (getPlayer() == null) {
            return;
        }

        if (getPlayerData().isSleepTimerEnabled() && System.currentTimeMillis() - mSleepTimerStartMs > 2 * 60 * 60 * 1_000) {
            getPlayer().setPlayWhenReady(false);
            getPlayer().setTitle(getContext().getString(R.string.sleep_timer));
            getPlayer().showOverlay(true);
            Helpers.enableScreensaver(getActivity());
        }
    }

    /**
     * Force load and play!
     */
    private void loadVideo(Video item) {
        if (getPlayer() != null && item != null) {
            mPlaylist.setCurrent(item);
            getPlayer().setVideo(item);
            getPlayer().resetPlayerState();
            loadFormatInfo(item);
        }
    }

    /**
     * Force load suggestions.
     */
    private void loadSuggestions(Video item) {
        if (item != null) {
            mPlaylist.setCurrent(item);
            getPlayer().setVideo(item);
            mSuggestionsController.loadSuggestions(item);
        }
    }

    private void waitMetadataSync(Video current, boolean showLoadingMsg) {
        if (current == null) {
            return;
        }

        if (current.nextMediaItem != null) {
            openVideoInt(Video.from(current.nextMediaItem));
        } else if (!current.isSynced) { // Maybe there's nothing left. E.g. when casting from phone
            // Wait in a loop while suggestions have been loaded...
            if (showLoadingMsg) {
                MessageHelpers.showMessageThrottled(getContext(), R.string.wait_data_loading);
            }
            // Short videos next fix (suggestions aren't loaded yet)
            boolean isEnded = getPlayer() != null && Math.abs(getPlayer().getDurationMs() - getPlayer().getPositionMs()) < 100;
            if (isEnded) {
                Utils.postDelayed(mMetadataSync, 1_000);
            }
        }
    }

    private void loadFormatInfo(Video video) {
        if (getPlayer() == null) {
            return;
        }

        getPlayer().showProgressBar(true);
        disposeActions();

        ServiceManager service = YouTubeServiceManager.instance();
        MediaItemService mediaItemManager = service.getMediaItemService();
        mFormatInfoAction = mediaItemManager.getFormatInfoObserve(video.videoId)
                .subscribe(this::processFormatInfo,
                           error -> {
                               getPlayer().showProgressBar(false);
                               runFormatErrorAction(error);
                           });
    }

    private void processFormatInfo(MediaItemFormatInfo formatInfo) {
        PlaybackView player = getPlayer();

        if (player == null || getVideo() == null) {
            return;
        }

        String bgImageUrl = null;

        getVideo().sync(formatInfo);

        // Fix stretched video for a couple milliseconds (before the onVideoSizeChanged gets called)
        applyAspectRatio(formatInfo);

        if (formatInfo.getPaidContentText() != null && getContentBlockData().isPaidContentNotificationEnabled()) {
            MessageHelpers.showMessage(getContext(), formatInfo.getPaidContentText());
        }

        if (formatInfo.isUnplayable()) {
            if (isEmbedPlayer()) {
                player.finish();
                return;
            }

            player.setTitle(formatInfo.getPlayabilityStatus());
            player.showProgressBar(false);
            mSuggestionsController.loadSuggestions(getVideo());
            bgImageUrl = getVideo().getBackgroundUrl();

            // 18+ video or the video is hidden/removed
            scheduleNextVideoTimer(5_000);

            //if (formatInfo.isUnknownError()) { // the bot error or the video not available
            //    scheduleRebootAppTimer(5_000);
            //} else { // 18+ video or the video is hidden/removed
            //    scheduleNextVideoTimer(5_000);
            //}
        } else if (acceptAdaptiveFormats(formatInfo) && formatInfo.containsDashFormats()) {
            Log.d(TAG, "Loading regular video in dash format...");

            if (getPlayerTweaksData().isHighBitrateFormatsEnabled() && formatInfo.hasExtendedHlsFormats()) {
                player.openMerged(formatInfo, formatInfo.getHlsManifestUrl());
            } else {
                player.openDash(formatInfo);
            }

            //player.openSabr(formatInfo);
        } else if (acceptAdaptiveFormats(formatInfo) && formatInfo.containsSabrFormats()) {
            Log.d(TAG, "Loading video in sabr format...");
            player.openSabr(formatInfo);
        } else if (acceptDashLive(formatInfo)) {
            Log.d(TAG, "Loading live video (current or past live stream) in dash format...");
            player.openDashUrl(formatInfo.getDashManifestUrl());
        } else if (formatInfo.isLive() && formatInfo.containsHlsUrl()) {
            Log.d(TAG, "Loading live video (current or past live stream) in hls format...");
            player.openHlsUrl(formatInfo.getHlsManifestUrl());
        } else if (formatInfo.containsUrlFormats()) {
            Log.d(TAG, "Loading url list video. This is always LQ...");
            player.openUrlList(applyFix(formatInfo.createUrlList()));
        } else {
            Log.d(TAG, "Empty format info received. Seems future live translation. No video data to pass to the player.");
            player.setTitle(formatInfo.getPlayabilityStatus());
            player.showProgressBar(false);
            mSuggestionsController.loadSuggestions(getVideo());
            bgImageUrl = getVideo().getBackgroundUrl();
            scheduleReloadVideoTimer(30 * 1_000);
        }

        player.showBackground(bgImageUrl); // remove bg (if video playing) or set another bg
    }

    private void scheduleReloadVideoTimer(int delayMs) {
        if (getPlayer() == null) {
            return;
        }

        if (getPlayer().isEngineInitialized()) {
            Log.d(TAG, "Reloading the video...");
            getPlayer().showOverlay(true);
            Utils.postDelayed(mReloadVideo, delayMs);
        }
    }

    private void scheduleNextVideoTimer(int delayMs) {
        if (getPlayer() == null) {
            return;
        }

        if (getPlayer().isEngineInitialized()) {
            Log.d(TAG, "Starting the next video...");
            getPlayer().showOverlay(true);
            Utils.postDelayed(mLoadNext, delayMs);
        }
    }

    private void scheduleRebootAppTimer(int delayMs) {
        if (getPlayer() != null) {
            Log.d(TAG, "Rebooting the app...");
            getPlayer().showOverlay(true);
            Utils.postDelayed(mRebootApp, delayMs);
        }
    }

    private void scheduleRestartEngineTimer(int delayMs) {
        if (getPlayer() != null) {
            Log.d(TAG, "Restarting the engine...");
            getPlayer().showOverlay(true);
            Utils.postDelayed(mRestartEngine, delayMs);
        }
    }

    private void openVideoInt(Video item) {
        if (item == null) {
            return;
        }

        disposeActions();

        if (item.hasVideo()) {
            // NOTE: Next clicked: instant playback even a mix
            // NOTE: Bypass PIP fullscreen on next caused by startView
            getMainController().onNewVideo(item);
            //getPlayer().showOverlay(true);
        } else {
            VideoActionPresenter.instance(getContext()).apply(item);
        }
    }

    private boolean isActionsRunning() {
        return RxHelper.isAnyActionRunning(mFormatInfoAction, mMpdStreamAction);
    }

    private void disposeActions() {
        mBufferingCount = null;
        MediaServiceManager.instance().disposeActions();
        RxHelper.disposeActions(mFormatInfoAction, mMpdStreamAction);
        Utils.removeCallbacks(mReloadVideo, mLoadNext, mRestartEngine, mMetadataSync, mOnLongBuffering, mRebootApp);
    }

    private void runFormatErrorAction(Throwable error) {
        if (isEmbedPlayer()) {
            if (getPlayer() != null) {
                getPlayer().finish();
            }
            return;
        }

        String message = error.getMessage();
        String className = error.getClass().getSimpleName();
        String fullMsg = String.format("loadFormatInfo error: %s: %s", className, Utils.getStackTraceAsString(error));
        Log.e(TAG, fullMsg);

        if (!Helpers.containsAny(message, "fromNullable result is null")) {
            MessageHelpers.showLongMessage(getContext(), fullMsg);
        }

        if (Helpers.containsAny(message, "Unexpected token", "Syntax error", "invalid argument") || // temporal fix
                Helpers.equalsAny(className, "PoTokenException", "BadWebViewException")) {
            YouTubeServiceManager.instance().applyNoPlaybackFix();
            reloadVideo();
        } else if (Helpers.containsAny(message, "is not defined")) {
            YouTubeServiceManager.instance().invalidateCache();
            reloadVideo();
        } else {
            Log.e(TAG, "Probably no internet connection");
            scheduleReloadVideoTimer(1_000);
        }
    }
    
    private void runEngineErrorAction(int type, int rendererIndex, Throwable error) {
        // Hide begin errors in embed mode (e.g. wrong date/time: unable to connect to...)
        if (isEmbedPlayer() && getPlayer() != null && getPlayer().getPositionMs() == 0) {
            getPlayer().finish();
            return;
        }

        if (getVideo() != null && getVideo().isLiveEnd) {
            // Url no longer works (e.g. live stream ended)
            loadNext();
            return;
        }

        boolean restart = applyEngineErrorAction(type, rendererIndex, error);

        if (restart) {
            restartEngine();
        } else {
            reloadVideo();
        }
    }

    private boolean applyEngineErrorAction(int type, int rendererIndex, Throwable error) {
        boolean restartEngine = true;
        boolean showMessage = true;
        String errorContent = error != null ? error.getMessage() : null;
        String errorTitle = getErrorTitle(type, rendererIndex);
        String errorMessage = errorTitle + "\n" + errorContent;

        if (Helpers.startsWithAny(errorContent, "Unable to connect to")) {
            // No internet connection or WRONG DATE on the device
            restartEngine = false;
        } else if (error instanceof OutOfMemoryError || (error != null && error.getCause() instanceof OutOfMemoryError)) {
            if (getPlayerTweaksData().getPlayerDataSource() == PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP) {
                // OkHttp has memory leak problems
                enableFasterDataSource();
            } else if (getPlayerData().getVideoBufferType() == PlayerData.BUFFER_HIGH || getPlayerData().getVideoBufferType() == PlayerData.BUFFER_HIGHEST) {
                getPlayerData().setVideoBufferType(PlayerData.BUFFER_MEDIUM);
            } else {
                getPlayerTweaksData().setSectionPlaylistEnabled(false);
                restartEngine = false;
            }
        } else if (Helpers.containsAny(errorContent, "Exception in CronetUrlRequest", "Response code: 503") && !getPlayerTweaksData().isNetworkErrorFixingDisabled()) {
            if (getVideo() != null && !getVideo().isLive) { // Finished live stream may provoke errors in Cronet
                getPlayerTweaksData().setPlayerDataSource(PlayerTweaksData.PLAYER_DATA_SOURCE_DEFAULT);
            } else {
                restartEngine = false;
            }
        } else if (type == PlayerEventListener.ERROR_TYPE_SOURCE && rendererIndex == PlayerEventListener.RENDERER_INDEX_UNKNOWN) {
            // NOTE: Starts with any (url deciphered incorrectly)
            // "Response code: 403" (poToken error, forbidden)
            // "Response code: 404" (not sure whether below helps)
            // "Response code: 503" (not sure whether below helps)
            // "Response code: 400" (not sure whether below helps)
            // "Response code: 429" (subtitle error, too many requests)
            // "Response code: 500" (subtitle error, generic server error)

            // NOTE: Fixing too many requests or network issues
            // NOTE: All these errors have unknown renderer (-1)
            // "Unable to connect to", "Invalid NAL length", "Response code: 421",
            // "Response code: 404", "Response code: 429", "Invalid integer size",
            // "Unexpected ArrayIndexOutOfBoundsException", "Unexpected IndexOutOfBoundsException"
            if (Helpers.startsWithAny(errorContent, "Response code: 403")) {
                YouTubeServiceManager.instance().applyNoPlaybackFix();
            } else if (getPlayer() != null && !FormatItem.SUBTITLE_NONE.equals(getPlayer().getSubtitleFormat())) {
                disableSubtitles(); // Response code: 429
                restartEngine = false;
                //YouTubeServiceManager.instance().applySubtitleFix();
            } else if (getPlayerTweaksData().isHighBitrateFormatsEnabled()) {
                getPlayerTweaksData().setHighBitrateFormatsEnabled(false); // Response code: 429
            } else {
                YouTubeServiceManager.instance().applyNoPlaybackFix(); // Response code: 403
            }
            restartEngine = false;
        } else if (type == PlayerEventListener.ERROR_TYPE_RENDERER && rendererIndex == PlayerEventListener.RENDERER_INDEX_SUBTITLE) {
            // "Response code: 429" (subtitle error)
            // "Response code: 500" (subtitle error)
            disableSubtitles();
            restartEngine = false;
        } else if (type == PlayerEventListener.ERROR_TYPE_RENDERER && rendererIndex == PlayerEventListener.RENDERER_INDEX_VIDEO) {
            getPlayerData().setFormat(FormatItem.VIDEO_FHD_AVC_30);
            if (getPlayerTweaksData().isSWDecoderForced()) {
                getPlayerTweaksData().setSWDecoderForced(false);
            } else {
                restartEngine = false;
            }
        } else if (type == PlayerEventListener.ERROR_TYPE_RENDERER && rendererIndex == PlayerEventListener.RENDERER_INDEX_AUDIO) {
            getPlayerData().setFormat(FormatItem.AUDIO_HQ_MP4A);
            restartEngine = false;
        } else if (type == PlayerEventListener.ERROR_TYPE_UNEXPECTED) {
            // Hide unknown errors on all devices
            showMessage = false;
        }

        if (showMessage) {
            MessageHelpers.showLongMessage(getContext(), errorMessage);
        }

        return restartEngine;
    }

    @SuppressLint("StringFormatMatches")
    private String getErrorTitle(int type, int rendererIndex) {
        String errorTitle;
        int msgResId;

        switch (type) {
            // Some ciphered data could be outdated.
            // Might happen when the app wasn't used quite a long time.
            case PlayerEventListener.ERROR_TYPE_SOURCE:
                switch (rendererIndex) {
                    case PlayerEventListener.RENDERER_INDEX_VIDEO:
                        msgResId = R.string.msg_player_error_video_source;
                        break;
                    case PlayerEventListener.RENDERER_INDEX_AUDIO:
                        msgResId = R.string.msg_player_error_audio_source;
                        break;
                    case PlayerEventListener.RENDERER_INDEX_SUBTITLE:
                        msgResId = R.string.msg_player_error_subtitle_source;
                        break;
                    default:
                        msgResId = R.string.unknown_source_error;
                }
                errorTitle = getContext().getString(msgResId);
                break;
            case PlayerEventListener.ERROR_TYPE_RENDERER:
                switch (rendererIndex) {
                    case PlayerEventListener.RENDERER_INDEX_VIDEO:
                        msgResId = R.string.msg_player_error_video_renderer;
                        break;
                    case PlayerEventListener.RENDERER_INDEX_AUDIO:
                        msgResId = R.string.msg_player_error_audio_renderer;
                        break;
                    case PlayerEventListener.RENDERER_INDEX_SUBTITLE:
                        msgResId = R.string.msg_player_error_subtitle_renderer;
                        break;
                    default:
                        msgResId = R.string.unknown_renderer_error;
                }
                errorTitle = getContext().getString(msgResId);
                break;
            case PlayerEventListener.ERROR_TYPE_UNEXPECTED:
                errorTitle = getContext().getString(R.string.msg_player_error_unexpected);
                break;
            default:
                errorTitle = getContext().getString(R.string.msg_player_error, type);
                break;
        }

        return errorTitle;
    }

    private void restartEngine() {
        scheduleRestartEngineTimer(1_000);
    }

    private void reloadVideo() {
        scheduleReloadVideoTimer(1_000);
    }

    private void rebootApp() {
        scheduleRebootAppTimer(1_000);
    }

    private List<String> applyFix(List<String> urlList) {
        // Sometimes top url cannot be played
        if (mLastErrorType == PlayerEventListener.ERROR_TYPE_SOURCE) {
            Collections.reverse(urlList);
        }

        return urlList;
    }

    private void applyPlaybackMode(int playbackMode) {
        if (getPlayer() == null) {
            return;
        }

        Video video = getVideo();
        // Fix simultaneous videos loading (e.g. when playback ends and user opens new video)
        if (video == null || isActionsRunning()) {
            return;
        }

        if (isEmbedPlayer()) {
            playbackMode = PlayerConstants.PLAYBACK_MODE_CLOSE;
        }

        switch (playbackMode) {
            case PlayerConstants.PLAYBACK_MODE_REVERSE_LIST:
                if (video.hasPlaylist() || video.belongsToChannelUploads() || video.belongsToChannel()) {
                    VideoGroup group = video.getGroup();
                    if (group != null && group.indexOf(video) != 0) { // stop after first
                        onPreviousClicked();
                    }
                    break;
                }
            case PlayerConstants.PLAYBACK_MODE_ALL:
            case PlayerConstants.PLAYBACK_MODE_SHUFFLE:
                loadNext();
                break;
            case PlayerConstants.PLAYBACK_MODE_ONE:
                getPlayer().setPositionMs(100); // fix frozen image on Android 4?
                break;
            case PlayerConstants.PLAYBACK_MODE_CLOSE:
                // Close player if suggestions not shown
                // Except when playing from queue
                if (mPlaylist.getNext() != null) {
                    loadNext();
                } else {
                    AppDialogPresenter dialog = getAppDialogPresenter();
                    if (!getPlayer().isSuggestionsShown() && (!dialog.isDialogShown() || dialog.isOverlay())) {
                        dialog.closeDialog();
                        getPlayer().finishReally();
                    }
                }
                break;
            case PlayerConstants.PLAYBACK_MODE_PAUSE:
                // Stop player after each video.
                // Except when playing from queue, if not overridden in settings
                if (mPlaylist.getNext() != null && !getPlayerTweaksData().isPauseAfterEachVideoInQueueEnabled()) {
                    loadNext();
                } else {
                    getPlayer().setPositionMs(getPlayer().getDurationMs());
                    getPlayer().setPlayWhenReady(false);
                    getPlayer().showSuggestions(true);
                }
                break;
            case PlayerConstants.PLAYBACK_MODE_LIST:
                // if video has a playlist load next or restart playlist
                if (video.hasNextPlaylist() || mPlaylist.getNext() != null) {
                    loadNext();
                } else {
                    getPlayer().setPositionMs(getPlayer().getDurationMs());
                    getPlayer().setPlayWhenReady(false);
                    getPlayer().showSuggestions(true);
                }
                break;
            case PlayerConstants.PLAYBACK_MODE_LOOP_LIST:
                // if video has a playlist load next or restart playlist
                if (video.hasNextPlaylist() || mPlaylist.getNext() != null) {
                    loadNext();
                } else {
                    restartPlaylist();
                }
                break;
            default:
                Log.e(TAG, "Undetected repeat mode " + playbackMode);
                break;
        }
    }

    private void restartPlaylist() {
        Video currentVideo = getVideo();
        VideoGroup group = currentVideo.getGroup(); // Get the VideoGroup (playlist)

        if (group != null && !group.isEmpty()) {
            // Clear current playlist
            mPlaylist.clear();

            // Add all videos from VideoGroup
            mPlaylist.addAll(group.getVideos());
            
            Video firstVideo = group.get(0);
            mPlaylist.setCurrent(firstVideo);
            openVideoInt(firstVideo);
        } else {
            Log.e(TAG, "VideoGroup is null or empty. Can't restart playlist.");
            getPlayer().setPositionMs(getPlayer().getDurationMs());
            getPlayer().setPlayWhenReady(false);
            getPlayer().showSuggestions(true);
        }
    }

    private boolean acceptAdaptiveFormats(MediaItemFormatInfo formatInfo) {
        if (getPlayerData().isLegacyCodecsForced() && formatInfo.containsUrlFormats()) {
            return false;
        }

        if (getPlayerTweaksData().isHlsStreamsForced() && formatInfo.isLive() && formatInfo.containsHlsUrl()) {
            return false;
        }

        // Not enough info for full length live streams
        if (formatInfo.isLive() && formatInfo.getStartTimeMs() == 0) {
            return false;
        }

        // Live dash url doesn't work with None buffer
        //if (formatInfo.isLive() && (getPlayerTweaksData().isDashUrlStreamsForced() || getPlayerData().getVideoBufferType() == PlayerData.BUFFER_NONE)) {
        if (formatInfo.isLive() && getPlayerTweaksData().isDashUrlStreamsForced() && formatInfo.containsDashUrl()) {
            return false;
        }

        if (formatInfo.isLive() && getPlayerTweaksData().isHlsStreamsForced() && formatInfo.containsHlsUrl()) {
            return false;
        }

        return true;
    }

    private boolean acceptDashLive(MediaItemFormatInfo formatInfo) {
        if (getPlayerTweaksData().isHlsStreamsForced() && formatInfo.isLive() && formatInfo.containsHlsUrl()) {
            return false;
        }

        return formatInfo.isLive() && formatInfo.containsDashUrl();
    }

    @Override
    public void onMetadata(MediaItemMetadata metadata) {
        loadRandomNext();
    }

    @Override
    public void onPlay() {
        Utils.removeCallbacks(mOnLongBuffering);
    }

    @Override
    public void onPause() {
        Utils.removeCallbacks(mOnLongBuffering);
    }

    private void loadRandomNext() {
        MediaServiceManager.instance().disposeActions();

        if (getPlayer() == null || getPlayerData() == null || getVideo() == null || getVideo().playlistInfo == null ||
                getPlayerData().getPlaybackMode() != PlayerConstants.PLAYBACK_MODE_SHUFFLE) {
            return;
        }

        if (getVideo().playlistInfo.getSize() != -1) {
            Video video = new Video();
            video.playlistId = getVideo().playlistId;
            video.playlistIndex = Utils.getRandomIndex(getVideo().playlistInfo.getCurrentIndex(), getVideo().playlistInfo.getSize());
            MediaServiceManager.instance().loadMetadata(video, randomMetadata -> {
                if (randomMetadata.getNextVideo() == null) {
                    return;
                }

                getVideo().nextMediaItem = SimpleMediaItem.from(randomMetadata);
                getPlayer().setNextTitle(Video.from(getVideo().nextMediaItem));
            });
        } else {
            VideoGroup topRow = getPlayer().getSuggestionsByIndex(0); // the playlist row
            if (topRow != null) {
                int currentIdx = topRow.indexOf(getVideo());
                int randomIndex = Utils.getRandomIndex(currentIdx, topRow.getSize());

                if (randomIndex != -1) {
                    Video nextVideo = topRow.get(randomIndex);
                    getVideo().nextMediaItem = SimpleMediaItem.from(nextVideo);
                    getPlayer().setNextTitle(nextVideo);
                }
            }
        }
    }

    private void loadRandomNext2() {
        if (getPlayer() == null || getPlayerData() == null || getVideo() == null || getVideo().isShuffled ||
                getVideo().shuffleMediaItem == null || getPlayerData().getPlaybackMode() != PlayerConstants.PLAYBACK_MODE_SHUFFLE) {
            return;
        }

        getVideo().isShuffled = true;
        getVideo().playlistParams = getVideo().shuffleMediaItem.getParams();
        getController(SuggestionsController.class).loadSuggestions(getVideo());
    }

    private void updateBufferingCountIfNeeded() {
        updateBufferingCount();
        if (isBufferingRecurrent()) {
            mBufferingCount = null;
            onLongBuffering();
        } else {
            // Count continuous buffering as a new occurrences....
            Utils.postDelayed(mOnLongBuffering, BUFFERING_CONTINUATION_MS);
        }
    }

    private void updateBufferingCount() {
        final long currentTimeMs = System.currentTimeMillis();
        int bufferingCount = 0;
        long previousTimeMs = 0;

        if (mBufferingCount != null) {
            bufferingCount = mBufferingCount.first;
            previousTimeMs = mBufferingCount.second;
        }

        if (currentTimeMs - previousTimeMs < BUFFERING_WINDOW_MS) {
            bufferingCount++;
        } else {
            bufferingCount = 1;
        }

        mBufferingCount = new Pair<>(bufferingCount, currentTimeMs);
    }

    private boolean isBufferingRecurrent() {
        return mBufferingCount != null && mBufferingCount.first > BUFFERING_RECURRENCE_COUNT;
    }

    private void switchNextEngine() {
        getPlayerTweaksData().setPlayerDataSource(getNextEngine());
        getPlayerTweaksData().persistNow();
    }

    private int getNextEngine() {
        int currentEngine = getPlayerTweaksData().getPlayerDataSource();
        Integer[] engineList = Utils.skipCronet() ?
                new Integer[] { PlayerTweaksData.PLAYER_DATA_SOURCE_DEFAULT, PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP } :
                new Integer[] { PlayerTweaksData.PLAYER_DATA_SOURCE_CRONET, PlayerTweaksData.PLAYER_DATA_SOURCE_DEFAULT, PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP };
        return Helpers.getNextValue(currentEngine, engineList);
    }

    private static int getFasterDataSource() {
        return Utils.skipCronet() ? PlayerTweaksData.PLAYER_DATA_SOURCE_DEFAULT : PlayerTweaksData.PLAYER_DATA_SOURCE_CRONET;
    }

    /**
     * Bad idea. Faster source is different among devices
     */
    private void enableFasterDataSource() {
        if (isFasterDataSourceEnabled()) {
            return;
        }

        getPlayerTweaksData().setPlayerDataSource(getFasterDataSource());
    }

    /**
     * Bad idea. Faster source is different among devices
     */
    private boolean isFasterDataSourceEnabled() {
        int fasterDataSource = getFasterDataSource();
        return getPlayerTweaksData().getPlayerDataSource() == fasterDataSource;
    }

    private int getPlaybackMode() {
        int playbackMode = getPlayerData().getPlaybackMode();

        Video video = getVideo();
        if (video != null && video.finishOnEnded) {
            playbackMode = PlayerConstants.PLAYBACK_MODE_CLOSE;
        } else if (video != null && video.belongsToShortsGroup() && getPlayerTweaksData().isLoopShortsEnabled()) {
            playbackMode = PlayerConstants.PLAYBACK_MODE_ONE;
        }
        return playbackMode;
    }

    /**
     * Fix stretched video for a couple milliseconds (before the onVideoSizeChanged gets called)
     */
    private void applyAspectRatio(MediaItemFormatInfo formatInfo) {
        if (getPlayer() == null) {
            return;
        }

        // Fix stretched video for a couple milliseconds (before the onVideoSizeChanged gets called)
        if (formatInfo.containsDashFormats()) {
            MediaFormat format = formatInfo.getAdaptiveFormats().get(0);
            int width = format.getWidth();
            int height = format.getHeight();
            boolean isShorts = width < height;
            if (width > 0 && height > 0 && (getPlayerData().getAspectRatio() == PlayerData.ASPECT_RATIO_DEFAULT || isShorts)) {
                getPlayer().setAspectRatio((float) width / height);
            } else {
                getPlayer().setAspectRatio(getPlayerData().getAspectRatio());
            }
        }
    }

    private void preloadNextVideoIfNeeded() {
        if (isEmbedPlayer() || getPlayer() == null || getVideo() == null || getVideo().isLive) {
            return;
        }

        if (getPlayer().getDurationMs() - getPlayer().getPositionMs() < 50_000) {
            MediaServiceManager.instance().loadFormatInfo(mSuggestionsController.getNext(), formatInfo -> {});
        }
    }

    private void disableSubtitles() {
        //if (getVideo() != null) {
        //    getPlayerData().disableSubtitlesPerChannel(getVideo().channelId);
        //}
        getPlayerData().setSubtitlesPerChannelEnabled(false);
        getPlayerData().setFormat(FormatItem.SUBTITLE_NONE);
    }
}
