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
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.UniqueRandom;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import io.reactivex.disposables.Disposable;

import java.util.Collections;
import java.util.List;

public class VideoLoaderController extends BasePlayerController {
    private static final String TAG = VideoLoaderController.class.getSimpleName();
    private static final long STREAM_END_THRESHOLD_MS = 180_000;
    private static final long BUFFERING_THRESHOLD_MS = 5_000;
    private static final long BUFFERING_WINDOW_MS = 60_000;
    private static final long BUFFERING_RECURRENCE_COUNT = (long) (BUFFERING_WINDOW_MS * 0.5 / BUFFERING_THRESHOLD_MS);
    private final Playlist mPlaylist;
    private Video mPendingVideo;
    private int mLastErrorType = -1;
    private SuggestionsController mSuggestionsController;
    private long mSleepTimerStartMs;
    private Disposable mFormatInfoAction;
    private Disposable mMpdStreamAction;
    private final Runnable mReloadVideo = () -> {
        getController(VideoStateController.class).saveState();
        loadVideo(getVideo());
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
        if (getPlayer() != null && video != null && video.hasVideo()) {
            Utils.restartTheApp(getContext(), video.videoId);
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

        boolean isVideoChanged = !item.equals(getVideo());

        if (!item.fromQueue) {
            mPlaylist.add(item);
        } else {
            item.fromQueue = false;
        }

        if (getPlayer() != null && getPlayer().isEngineInitialized()) { // player is initialized
            if (isVideoChanged || !getPlayer().containsMedia()) {
                loadVideo(item); // force play immediately
            } else {
                loadSuggestions(item); // update suggestions only
            }
        } else {
            mPendingVideo = item;
        }
    }

    @Override
    public void onBuffering() {
        Utils.postDelayed(mOnLongBuffering, BUFFERING_THRESHOLD_MS);
    }

    private void onLongBuffering() {
        if (getPlayer() == null || getVideo() == null) {
            return;
        }

        // Stream end check (hangs on buffering)
        if ((!getVideo().isLive || getVideo().isLiveEnd) &&
                getPlayer().getDurationMs() - getPlayer().getPositionMs() < STREAM_END_THRESHOLD_MS) {
            getMainController().onPlayEnd();
        } else {
            MessageHelpers.showLongMessage(getContext(), R.string.applying_fix);
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
        checkSleepTimer();
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

    @Override
    public void onFinish() {
        // ???
        //mPlaylist.clearPosition();
    }

    public void loadPrevious() {
        openVideoInt(mSuggestionsController.getPrevious());

        if (getPlayerTweaksData().isPlayerUiOnNextEnabled()) {
            getPlayer().showOverlay(true);
        }
    }

    public void loadNext() {
        Video next = mSuggestionsController.getNext();
        //getVideo() = null; // in case next video is the same as previous

        if (next != null) {
            forceSectionPlaylistIfNeeded(getVideo(), next);
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

        applyPlaybackMode(getPlaybackMode());
    }

    @Override
    public void onSuggestionItemClicked(Video item) {
        openVideoInt(item);

        getPlayer().showControls(false);
    }

    @Override
    public void onPlaybackQueueClicked() {
        AppDialogUtil.showPlaybackQueueDialog(getContext(), this::openVideoInt);
    }

    @Override
    public boolean onKeyDown(int keyCode) {
        if (getPlayer() == null) {
            return false;
        }

        mSleepTimerStartMs = System.currentTimeMillis();

        // Remove error msg if needed
        if (getPlayerData().isSonyTimerFixEnabled()) {
            getPlayer().setVideo(getVideo());
        }

        Utils.removeCallbacks(mRestartEngine);

        return false;
    }

    private void checkSleepTimer() {
        if (getPlayer() == null) {
            return;
        }

        if (getPlayerData().isSonyTimerFixEnabled() && System.currentTimeMillis() - mSleepTimerStartMs > 60 * 60 * 1_000) {
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
        if (getPlayer() == null) {
            return;
        }

        String bgImageUrl = null;

        getVideo().sync(formatInfo);

        // Fix stretched video for a couple milliseconds (before the onVideoSizeChanged gets called)
        applyAspectRatio(formatInfo);

        if (formatInfo.containsMedia()) {
            getStateService().setHistoryBroken(formatInfo.isHistoryBroken());
        }

        if (formatInfo.getPaidContentText() != null && getContentBlockData().isPaidContentNotificationEnabled()) {
            MessageHelpers.showMessage(getContext(), formatInfo.getPaidContentText());
        }

        if (formatInfo.isUnplayable()) {
            if (isEmbedPlayer()) {
                getPlayer().finish();
                return;
            }

            getPlayer().setTitle(formatInfo.getPlayabilityStatus());
            getPlayer().showProgressBar(false);
            mSuggestionsController.loadSuggestions(getVideo());
            bgImageUrl = getVideo().getBackgroundUrl();

            if (formatInfo.isHistoryBroken()) { // bot check error or the video is hidden
                YouTubeServiceManager.instance().applyNoPlaybackFix();
                scheduleRebootAppTimer(5_000);
            } else { // 18+ video
                scheduleNextVideoTimer(5_000);
            }
        } else if (acceptDashVideo(formatInfo)) {
            Log.d(TAG, "Found regular video in dash format. Loading...");

            mMpdStreamAction = formatInfo.createMpdStreamObservable()
                    .subscribe(
                            dashManifest -> {
                                if (getPlayerTweaksData().isHighBitrateFormatsEnabled() && formatInfo.hasExtendedHlsFormats()) {
                                    getPlayer().openMerged(dashManifest, formatInfo.getHlsManifestUrl());
                                } else {
                                    getPlayer().openDash(dashManifest);
                                }
                            },
                            error -> Log.e(TAG, "createMpdStream error: %s", error.getMessage())
                    );
        } else if (acceptDashLive(formatInfo)) {
            Log.d(TAG, "Found live video (current or past live stream) in dash format. Loading...");
            getPlayer().openDashUrl(formatInfo.getDashManifestUrl());
        } else if (formatInfo.isLive() && formatInfo.containsHlsUrl()) {
            Log.d(TAG, "Found live video (current or past live stream) in hls format. Loading...");
            getPlayer().openHlsUrl(formatInfo.getHlsManifestUrl());
        } else if (formatInfo.containsUrlFormats()) {
            Log.d(TAG, "Found url list video. This is always LQ. Loading...");
            getPlayer().openUrlList(applyFix(formatInfo.createUrlList()));
        } else {
            Log.d(TAG, "Empty format info received. Seems future live translation. No video data to pass to the player.");
            getPlayer().setTitle(formatInfo.getPlayabilityStatus());
            getPlayer().showProgressBar(false);
            mSuggestionsController.loadSuggestions(getVideo());
            bgImageUrl = getVideo().getBackgroundUrl();
            scheduleReloadVideoTimer(30 * 1_000);
        }

        getPlayer().showBackground(bgImageUrl); // remove bg (if video playing) or set another bg

        //if (bgImageUrl != null && getPlayer().containsMedia()) {
        //    // Make background visible
        //    getPlayer().restartEngine();
        //}
    }

    private void scheduleReloadVideoTimer(int delayMs) {
        if (getPlayer().isEngineInitialized()) {
            Log.d(TAG, "Starting check for the future stream...");
            getPlayer().showOverlay(true);
            Utils.postDelayed(mReloadVideo, delayMs);
        }
    }

    private void scheduleNextVideoTimer(int delayMs) {
        if (getPlayer().isEngineInitialized()) {
            Log.d(TAG, "Starting next video after delay...");
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
            Log.d(TAG, "Rebooting the app...");
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

        boolean restart = applyEngineErrorAction(type, rendererIndex, error);

        if (restart) {
            restartEngine();
        } else {
            reloadVideo();
        }
    }

    private boolean applyEngineErrorAction(int type, int rendererIndex, Throwable error) {
        boolean restartEngine = true;
        String message = error != null ? error.getMessage() : null;
        String errorTitle = getErrorTitle(type, rendererIndex);
        String shortErrorMsg = errorTitle + "\n" + message;
        String fullErrorMsg = shortErrorMsg + "\n" + getContext().getString(R.string.applying_fix);
        String resultMsg = fullErrorMsg;

        if (Helpers.startsWithAny(message, "Unable to connect to")) {
            // No internet connection or WRONG DATE on the device
            restartEngine = false;
            resultMsg = shortErrorMsg;
        } else if (error instanceof OutOfMemoryError || (error != null && error.getCause() instanceof OutOfMemoryError)) {
            if (getPlayerTweaksData().getPlayerDataSource() == PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP) {
                // OkHttp has memory leak problems
                enableFasterDataSource();
            } else if (getPlayerData().getVideoBufferType() == PlayerData.BUFFER_MEDIUM || getPlayerData().getVideoBufferType() == PlayerData.BUFFER_LOW) {
                getPlayerTweaksData().enableSectionPlaylist(false);
                restartEngine = false;
            } else {
                getPlayerData().setVideoBufferType(PlayerData.BUFFER_MEDIUM);
            }
        } else if (Helpers.containsAny(message, "Exception in CronetUrlRequest")) {
            if (getVideo() != null && !getVideo().isLive) { // Finished live stream may provoke errors in Cronet
                getPlayerTweaksData().setPlayerDataSource(PlayerTweaksData.PLAYER_DATA_SOURCE_DEFAULT);
            } else {
                restartEngine = false;
            }
        } else if (Helpers.startsWithAny(message, "Response code: 403", "Response code: 404", "Response code: 503")) {
            // "Response code: 403" (url deciphered incorrectly)
            // "Response code: 404" (not sure whether below helps)
            // "Response code: 503" (not sure whether below helps)
            // "Response code: 400" (not sure whether below helps)
            YouTubeServiceManager.instance().applyNoPlaybackFix();
            restartEngine = false;
        } else if (Helpers.startsWithAny(message, "Response code: 429", "Response code: 400")) {
            YouTubeServiceManager.instance().applyAntiBotFix();
            restartEngine = false;
        } else if (type == PlayerEventListener.ERROR_TYPE_SOURCE && rendererIndex == PlayerEventListener.RENDERER_INDEX_UNKNOWN) {
            // NOTE: Fixing too many requests or network issues
            // NOTE: All these errors have unknown renderer (-1)
            // "Unable to connect to", "Invalid NAL length", "Response code: 421",
            // "Response code: 404", "Response code: 429", "Invalid integer size",
            // "Unexpected ArrayIndexOutOfBoundsException", "Unexpected IndexOutOfBoundsException"
            // "Response code: 403" (url deciphered incorrectly)
            YouTubeServiceManager.instance().applyAntiBotFix();
            restartEngine = false;
        } else if (type == PlayerEventListener.ERROR_TYPE_RENDERER && rendererIndex == PlayerEventListener.RENDERER_INDEX_SUBTITLE) {
            // "Response code: 500"
            if (getVideo() != null) {
                getPlayerData().disableSubtitlesPerChannel(getVideo().channelId);
                getPlayerData().setFormat(getPlayerData().getDefaultSubtitleFormat());
            }
            restartEngine = false; // ???
        } else if (type == PlayerEventListener.ERROR_TYPE_RENDERER && rendererIndex == PlayerEventListener.RENDERER_INDEX_VIDEO) {
            //FormatItem videoFormat = getPlayerData().getFormat(FormatItem.TYPE_VIDEO);
            //if (!videoFormat.isPreset()) {
            //    getPlayerData().setFormat(getPlayerData().getDefaultVideoFormat());
            //}
            getPlayerData().setFormat(FormatItem.VIDEO_FHD_AVC_30);
            if (getPlayerTweaksData().isSWDecoderForced()) {
                getPlayerTweaksData().forceSWDecoder(false);
            } else {
                restartEngine = false;
            }
        } else if (type == PlayerEventListener.ERROR_TYPE_RENDERER && rendererIndex == PlayerEventListener.RENDERER_INDEX_AUDIO) {
            //getPlayerData().setFormat(getPlayerData().getDefaultAudioFormat());
            getPlayerData().setFormat(FormatItem.AUDIO_HQ_MP4A);
            restartEngine = false;
        } else {
            resultMsg = shortErrorMsg;
        }

        // Hide unknown errors on all devices
        if (type != PlayerEventListener.ERROR_TYPE_UNEXPECTED) {
            MessageHelpers.showLongMessage(getContext(), resultMsg);
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
        // Give a time to user to do something
        Utils.postDelayed(mRestartEngine, 1_000);
    }

    private void reloadVideo() {
        // Give a time to user to do something
        Utils.postDelayed(mReloadVideo, 1_000);
    }

    private List<String> applyFix(List<String> urlList) {
        // Sometimes top url cannot be played
        if (mLastErrorType == PlayerEventListener.ERROR_TYPE_SOURCE) {
            Collections.reverse(urlList);
        }

        return urlList;
    }

    private void applyPlaybackMode(int playbackMode) {
        Video video = getVideo();
        // Fix simultaneous videos loading (e.g. when playback ends and user opens new video)
        if (video == null || isActionsRunning()) {
            return;
        }

        // Stop the playback if the user is browsing options or reading comments
        if (getAppDialogPresenter().isDialogShown() && !getAppDialogPresenter().isOverlay() && playbackMode != PlayerConstants.PLAYBACK_MODE_ONE) {
            getAppDialogPresenter().setOnFinish(mOnApplyPlaybackMode);
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
                // Except when playing from queue
                if (mPlaylist.getNext() != null) {
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

    private boolean acceptDashVideo(MediaItemFormatInfo formatInfo) {
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

        return formatInfo.containsDashVideoFormats();
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

        if (getPlayer() == null || getPlayerData() == null || getVideo() == null || getVideo().playlistInfo == null) {
            return;
        }

        if (getPlayerData().getPlaybackMode() == PlayerConstants.PLAYBACK_MODE_SHUFFLE) {
            Video video = new Video();
            video.playlistId = getVideo().playlistId;
            video.playlistIndex = UniqueRandom.getRandomIndex(getVideo().playlistInfo.getCurrentIndex(), getVideo().playlistInfo.getSize());

            MediaServiceManager.instance().loadMetadata(video, randomMetadata -> {
                if (randomMetadata.getNextVideo() == null) {
                    return;
                }

                getVideo().nextMediaItem = SimpleMediaItem.from(randomMetadata);
                getPlayer().setNextTitle(Video.from(getVideo().nextMediaItem));
            });
        }
    }

    private void updateBufferingCountIfNeeded() {
        updateBufferingCount();
        if (isBufferingRecurrent()) {
            mBufferingCount = null;
            onLongBuffering();
        } else {
            // Continue counting buffering occurrences...
            onBuffering();
        }
    }

    private void updateBufferingCount() {
        long currentTimeMs = System.currentTimeMillis();
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

    private void forceSectionPlaylistIfNeeded(Video previous, Video next) {
        if (previous == null || next == null) {
            return;
        }

        // Force to all subsequent videos in section playlist row
        if (previous.isSectionPlaylistEnabled(getContext())) {
            previous.forceSectionPlaylist = false;
            next.forceSectionPlaylist = true;
        }
    }

    private void switchNextEngine() {
        getPlayerTweaksData().setPlayerDataSource(getNextEngine());
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
        if (getGeneralData().isProxyEnabled()) {
            // Disable auto switch for proxies.
            // Current source may have better compatibility with proxies than fastest one.
            return true;
        }

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
        // Fix stretched video for a couple milliseconds (before the onVideoSizeChanged gets called)
        if (formatInfo.containsDashFormats()) {
            MediaFormat format = formatInfo.getDashFormats().get(0);
            int width = format.getWidth();
            int height = format.getHeight();
            boolean isShorts = width < height;
            if (width > 0 && height > 0 && (getPlayerData().getAspectRatio() == PlayerData.ASPECT_RATIO_DEFAULT || isShorts)) {
                getPlayer().setAspectRatio((float) width / height);
            }
        }
    }
}
