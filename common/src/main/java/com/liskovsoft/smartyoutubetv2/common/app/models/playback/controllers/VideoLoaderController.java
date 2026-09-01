package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import android.os.Build.VERSION;

import com.google.android.exoplayer2.source.sabr.SabrLiveFeatureFlags;
import com.google.android.exoplayer2.source.sabr.parser.exceptions.SabrMediaCorrelationException;
import com.google.android.exoplayer2.source.sabr.parser.ump.UMPProtocolException;
import com.google.android.exoplayer2.source.sabr.session.SabrSessionException;
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
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.live.LivePlaybackDescriptor;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.live.LivePlaybackSession;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.live.LivePlaybackSourceSelector;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.live.LivePlaybackStatus;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.VideoActionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import io.reactivex.disposables.Disposable;

public class VideoLoaderController extends BasePlayerController {
    private static final String TAG = VideoLoaderController.class.getSimpleName();
    private static final int MIN_SHUFFLE_SIZE = 30;
    private final Playlist mPlaylist;
    private Video mPendingVideo;
    private SuggestionsController mSuggestionsController;
    private ErrorFixerController mErrorFixerController;
    private long mSleepTimerStartMs;
    private Disposable mFormatInfoAction;
    private final LivePlaybackSession mLivePlaybackSession =
            new LivePlaybackSession(new LivePlaybackSourceSelector());
    private MediaItemFormatInfo mLiveFormatInfo;
    private boolean mLiveFallbackHandled;
    private boolean mLiveTerminalFailure;
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
    private final Runnable mOnApplyPlaybackMode = () -> {
        if (getPlayer() != null && getPlayer().getPositionMs() >= getPlayer().getDurationMs()) {
            applyPlaybackMode(getPlaybackMode());
        }
    };
    private final Runnable mShowProgressBar = () -> {
        if (getPlayer() != null) {
            getPlayer().showProgressBar(true);
        }
    };

    public VideoLoaderController() {
        mPlaylist = Playlist.instance();
    }

    @Override
    public void onInit() {
        mSuggestionsController = getController(SuggestionsController.class);
        mErrorFixerController = getController(ErrorFixerController.class);
        mSleepTimerStartMs = System.currentTimeMillis();
    }

    @Override
    public void onNewVideo(Video item) {
        if (item == null) {
            return;
        }

        mLiveTerminalFailure = false;
        stopLivePlaybackSession();

        item.isShuffled = false;

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
        stopLivePlaybackSession();
    }

    @Override
    public void onEngineError(int type, int rendererIndex, Throwable error) {
        mLiveFallbackHandled = false;
        if (mLiveFormatInfo == null || getPlayer() == null
                || mLivePlaybackSession.getCurrentSource() == null) {
            return;
        }
        boolean sourceFailure = type == com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener.ERROR_TYPE_SOURCE;
        LivePlaybackSession.Failure failure = classifyLiveFailure(error);
        if (!sourceFailure && failure == LivePlaybackSession.Failure.UNKNOWN) {
            return;
        }
        mLiveFallbackHandled = advanceLiveFallback(failure);
    }

    /** Routes live stalls through the same bounded source budget as explicit engine failures. */
    public boolean handleLiveNoProgress() {
        if (mLiveTerminalFailure) {
            return true;
        }
        return advanceLiveFallback(LivePlaybackSession.Failure.NO_PROGRESS);
    }

    private boolean advanceLiveFallback(LivePlaybackSession.Failure failure) {
        if (mLiveFormatInfo == null || getPlayer() == null
                || mLivePlaybackSession.getCurrentSource() == null) {
            return false;
        }
        LivePlaybackSourceSelector.Decision fallback = mLivePlaybackSession.fail(failure);
        LivePlaybackStatus.update(mLiveFormatInfo.getVideoId(), mLivePlaybackSession);
        if (!fallback.isAvailable()) {
            mLiveTerminalFailure = true;
            getPlayer().showProgressBar(false);
            getPlayer().setTitle(fallback.reason);
            getPlayer().showOverlay(true);
            return true;
        }
        Log.e(TAG, "Live source fallback: %s -> %s", failure, fallback.source);
        getPlayer().showProgressBar(true);
        openLiveSource(fallback);
        return true;
    }

    public boolean consumeLiveFallbackHandled() {
        // ExoPlayer may report more than one callback for the same terminal source error. Keep
        // those callbacks inside the exhausted live session instead of handing the duplicate to
        // the legacy generic fixer, which would reload the video and restart the source budget.
        boolean handled = mLiveFallbackHandled ||
                mLiveTerminalFailure ||
                (mLiveFormatInfo != null && mLivePlaybackSession.isTerminal());
        mLiveFallbackHandled = false;
        return handled;
    }

    @Override
    public void onBuffering() {
        updateLivePlaybackState(LivePlaybackSession.State.BUFFERING);
    }

    @Override
    public void onPlay() {
        updateLivePlaybackState(isPlayingBehindLiveEdge()
                ? LivePlaybackSession.State.PLAYING_DVR
                : LivePlaybackSession.State.PLAYING_LIVE_EDGE);
    }

    @Override
    public void onPause() {
        updateLivePlaybackState(LivePlaybackSession.State.PAUSED);
    }

    @Override
    public void onSeekEnd() {
        onPlay();
    }

    @Override
    public void onSeekPositionChanged(long positionMs) {
        if (mLiveFormatInfo != null && mLiveFormatInfo.isStreamSeekable()) {
            updateLivePlaybackState(LivePlaybackSession.State.PLAYING_DVR);
        }
    }

    @Override
    public void onVideoLoaded(Video video) {
        if (getPlayer() == null) {
            return;
        }
        
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
        mSleepTimerStartMs = System.currentTimeMillis();

        // Remove error msg if needed
        if (getPlayer() != null && getPlayerData().getSleepTimerHours() > 0) {
            getPlayer().setVideo(getVideo());
        }

        Utils.removeCallbacks(mRestartEngine);

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

        float sleepHours = getPlayerData().getSleepTimerHours();
        if (sleepHours > 0 && System.currentTimeMillis() - mSleepTimerStartMs > sleepHours * 60 * 60 * 1_000) {
            getPlayer().setPlayWhenReady(false);
            getPlayer().setTitle(getContext().getString(R.string.player_sleep_timer)
                    + " (" + getContext().getResources().getQuantityString(R.plurals.hours, (int) sleepHours, Helpers.toString(sleepHours)) + ")");
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
        if (getPlayer() == null) {
            return;
        }

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
                MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);
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

        // Fix no progress on next video (the engine may still buffering a bit)
        //getPlayer().showProgressBar(true);
        Utils.post(mShowProgressBar);
        disposeActions();

        ServiceManager service = YouTubeServiceManager.instance();
        MediaItemService mediaItemManager = service.getMediaItemService();
        mFormatInfoAction = mediaItemManager.getFormatInfoObserve(video.videoId)
                .subscribe(this::processFormatInfo,
                           error -> {
                               getPlayer().showProgressBar(false);
                               mErrorFixerController.runFormatErrorAction(error);
                           });
    }

    private void processFormatInfo(MediaItemFormatInfo formatInfo) {
        PlaybackView player = getPlayer();

        if (player == null || getVideo() == null) {
            return;
        }

        String bgImageUrl = null;

        getVideo().sync(formatInfo);

        player.setRequestContext(formatInfo);

        // Fix stretched video for a couple milliseconds (before the onVideoSizeChanged gets called)
        applyAspectRatio(formatInfo);

        if (formatInfo.getPaidContentText() != null && getSponsorBlockData().isPaidContentNotificationEnabled()) {
            MessageHelpers.showMessage(getContext(), formatInfo.getPaidContentText());
        }

        if (formatInfo.isUnplayable()) {
            if (isEmbedPlayer()) {
                player.finish();
                return;
            }

            player.setTitle(formatInfo.getPlayabilityReason());
            player.showProgressBar(false);
            mSuggestionsController.loadSuggestions(getVideo());
            bgImageUrl = getVideo().getBackgroundUrl();

            // 18+ video or the video is hidden/removed
            player.showOverlay(true);
            loadNextVideo(5_000);

            //if (formatInfo.isUnknownError()) { // the bot error or the video not available
            //    scheduleRebootAppTimer(5_000);
            //} else { // 18+ video or the video is hidden/removed
            //    scheduleNextVideoTimer(5_000);
            //}
        } else if (formatInfo.isLive() || formatInfo.isLiveContent()) {
            processLiveFormatInfo(formatInfo);
        } else if (acceptAdaptiveFormats(formatInfo) && formatInfo.containsDashFormats()) {
            Log.d(TAG, "Loading regular video in dash format...");

            if (getPlayerTweaksData().isHighBitrateFormatsEnabled() && formatInfo.hasExtendedHlsFormats()) {
                player.openMerged(formatInfo, formatInfo.getHlsManifestUrl());
            } else {
                player.openDash(formatInfo);
            }
        } else if (acceptAdaptiveFormats(formatInfo) && formatInfo.containsSabrFormats() && !formatInfo.isLive()) { // TODO: SABR live not implemented yet
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
            player.openUrlList(formatInfo.createUrlList());
        } else {
            Log.d(TAG, "Empty format info received. Seems future live translation. No video data to pass to the player.");
            player.setTitle(formatInfo.getPlayabilityReason());
            player.showProgressBar(false);
            mSuggestionsController.loadSuggestions(getVideo());
            bgImageUrl = getVideo().getBackgroundUrl();
            player.showOverlay(true);
            reloadVideo(30 * 1_000);
        }

        player.showBackground(bgImageUrl); // remove bg (if video playing) or set another bg
    }

    private void processLiveFormatInfo(MediaItemFormatInfo formatInfo) {
        mLiveFormatInfo = formatInfo;
        boolean debugHarnessEnabled = SabrLiveFeatureFlags.enableSabrLiveHarness();
        boolean sabrEnabled = SabrLiveFeatureFlags.enableSabrLiveProduction()
                || debugHarnessEnabled;
        // The debug route is a one-shot opt-in for this resolved playback, not global app state.
        if (debugHarnessEnabled) {
            SabrLiveFeatureFlags.setSabrLiveHarnessEnabledForDebug(false);
        }
        LivePlaybackSourceSelector.Configuration configuration =
                new LivePlaybackSourceSelector.Configuration(
                        sabrEnabled,
                        acceptAdaptiveFormats(formatInfo),
                        acceptDashLive(formatInfo),
                        formatInfo.containsHlsUrl());
        LivePlaybackSourceSelector.Decision decision = mLivePlaybackSession.start(
                LivePlaybackDescriptor.from(formatInfo), configuration);
        LivePlaybackStatus.update(formatInfo.getVideoId(), mLivePlaybackSession);
        if (decision.isAvailable()) {
            openLiveSource(decision);
            return;
        }
        PlaybackView player = getPlayer();
        if (player != null) {
            mLiveTerminalFailure = true;
            player.setTitle(formatInfo.getPlayabilityReason() != null
                    ? formatInfo.getPlayabilityReason() : decision.reason);
            player.showProgressBar(false);
            player.showOverlay(true);
        }
    }

    private boolean isPlayingBehindLiveEdge() {
        PlaybackView player = getPlayer();
        return player != null && mLiveFormatInfo != null && mLiveFormatInfo.isStreamSeekable()
                && player.getDurationMs() > 0
                && player.getDurationMs() - player.getPositionMs() > 30_000;
    }

    private void updateLivePlaybackState(LivePlaybackSession.State state) {
        if (mLiveFormatInfo == null) return;
        mLivePlaybackSession.updatePlaybackState(state);
        LivePlaybackStatus.update(mLiveFormatInfo.getVideoId(), mLivePlaybackSession);
    }

    private void openLiveSource(LivePlaybackSourceSelector.Decision decision) {
        PlaybackView player = getPlayer();
        if (player == null || mLiveFormatInfo == null) return;
        Log.d(TAG, "Loading live video with %s (%s)", decision.source, decision.reason);
        switch (decision.source) {
            case SABR:
                player.openSabr(mLiveFormatInfo);
                break;
            case DASH_FORMATS:
                player.openDash(mLiveFormatInfo);
                break;
            case DASH_MANIFEST:
                player.openDashUrl(mLiveFormatInfo.getDashManifestUrl());
                break;
            case HLS:
                player.openHlsUrl(mLiveFormatInfo.getHlsManifestUrl());
                break;
        }
    }

    private LivePlaybackSession.Failure classifyLiveFailure(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof UMPProtocolException
                    || cause instanceof SabrMediaCorrelationException) {
                return LivePlaybackSession.Failure.PROTOCOL;
            }
            if (cause instanceof SabrSessionException) {
                switch (((SabrSessionException) cause).getCategory()) {
                    case PROTECTION: return LivePlaybackSession.Failure.TOKEN;
                    case RELOAD_REQUIRED:
                    case RELOAD_FAILED:
                    case REBOOTSTRAP_REQUIRED: return LivePlaybackSession.Failure.RELOAD;
                    case UNAVAILABLE:
                    case UNSUPPORTED_FORMAT: return LivePlaybackSession.Failure.CAPABILITY;
                    case PROTOCOL:
                    case MEDIA_CORRELATION: return LivePlaybackSession.Failure.PROTOCOL;
                    case CANCELLED:
                    case STALE_GENERATION: return LivePlaybackSession.Failure.CANCELLED;
                    default: return LivePlaybackSession.Failure.INITIALIZATION;
                }
            }
        }
        LivePlaybackSourceSelector.Source source = mLivePlaybackSession.getCurrentSource();
        if (source == LivePlaybackSourceSelector.Source.DASH_FORMATS
                || source == LivePlaybackSourceSelector.Source.DASH_MANIFEST) {
            return LivePlaybackSession.Failure.DASH;
        }
        if (source == LivePlaybackSourceSelector.Source.HLS) {
            return LivePlaybackSession.Failure.HLS;
        }
        return LivePlaybackSession.Failure.UNKNOWN;
    }

    private void stopLivePlaybackSession() {
        if (mLiveFormatInfo != null || mLivePlaybackSession.getCurrentSource() != null) {
            mLivePlaybackSession.stop();
            LivePlaybackStatus.clear();
        }
        mLiveFormatInfo = null;
        mLiveFallbackHandled = false;
    }

    private void reloadVideo(int delayMs) {
        if (getPlayer() == null) {
            return;
        }

        if (getPlayer().isEngineInitialized()) {
            Log.d(TAG, "Reloading the video...");
            Utils.postDelayed(mReloadVideo, delayMs);
        }
    }

    private void loadNextVideo(int delayMs) {
        if (getPlayer() == null) {
            return;
        }

        if (getPlayer().isEngineInitialized()) {
            Log.d(TAG, "Starting the next video...");
            Utils.postDelayed(mLoadNext, delayMs);
        }
    }

    private void restartEngine(int delayMs) {
        if (getPlayer() != null) {
            Log.d(TAG, "Restarting the engine...");
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
        return RxHelper.isAnyActionRunning(mFormatInfoAction);
    }

    private void disposeActions() {
        MediaServiceManager.instance().disposeActions();
        RxHelper.disposeActions(mFormatInfoAction);
        Utils.removeCallbacks(mReloadVideo, mLoadNext, mRestartEngine, mMetadataSync);
    }

    public void restartEngine() {
        restartEngine(1_000);
    }

    public void reloadVideo() {
        reloadVideo(1_000);
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
                if (VERSION.SDK_INT <= 19) {
                    // Fix frozen image on Android 4
                    restartEngine();
                } else {
                    getPlayer().setPositionMs(0);
                }
                break;
            case PlayerConstants.PLAYBACK_MODE_CLOSE:
                // Close player if suggestions not shown
                // Except when playing from queue
                if (mPlaylist.getNext() != null && !getPlayerTweaksData().isQueueRespectsPlaybackMode()) {
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
                if (mPlaylist.getNext() != null && !getPlayerTweaksData().isQueueRespectsPlaybackMode()) {
                    loadNext();
                } else {
                    stopPlayback();
                }
                break;
            case PlayerConstants.PLAYBACK_MODE_LIST:
                // if video has a playlist load next or restart playlist
                if (video.hasNextPlaylist() || mPlaylist.getNext() != null) {
                    loadNext();
                } else {
                    //restartPlaylistIfNeeded();
                    stopPlayback();
                }
                break;
            default:
                Log.e(TAG, "Undetected repeat mode " + playbackMode);
                break;
        }
    }

    private void stopPlayback() {
        if (getPlayer() == null) {
            return;
        }

        getPlayer().setPositionMs(getPlayer().getDurationMs());
        getPlayer().setPlayWhenReady(false);
        getPlayer().showSuggestions(true);
    }

    private void restartPlaylistIfNeeded() {
        if (getPlayer() == null || getVideo() == null) {
            return;
        }
        
        VideoGroup group = getVideo().getGroup(); // Get the VideoGroup (playlist)

        if (group != null && !group.isEmpty() && getVideo().belongsToSamePlaylistGroup()) {
            openVideoInt(group.get(0));
        } else {
            Log.e(TAG, "VideoGroup is null or empty. Can't restart playlist.");
            stopPlayback();
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
        initRandomNext();
    }

    private void initRandomNext() {
        MediaServiceManager.instance().disposeActions();

        PlaybackView player = getPlayer();
        PlayerData playerData = getPlayerData();
        Video current = getVideo();

        if (player == null || playerData == null || current == null || current.playlistInfo == null ||
                playerData.getPlaybackMode() != PlayerConstants.PLAYBACK_MODE_SHUFFLE) {
            return;
        }

        // NOTE: Shuffle only user created playlists (size != -1)
        if (current.playlistInfo.getSize() > MIN_SHUFFLE_SIZE) {
            Video video = new Video();
            video.playlistId = current.playlistId;
            video.playlistIndex = Utils.getRandomIndex(current.playlistInfo.getCurrentIndex(), current.playlistInfo.getSize());
            MediaServiceManager.instance().loadMetadata(video, randomMetadata -> {
                if (randomMetadata.getNextVideo() == null) {
                    return;
                }

                current.nextMediaItem = SimpleMediaItem.from(randomMetadata);
                current.isShuffled = true;
                player.setNextTitle(Video.from(current.nextMediaItem));
            });
        }
        //else {
        //    VideoGroup topRow = player.getSuggestionsByIndex(0); // the playlist row
        //
        //    if (topRow != null && topRow.isChapters()) {
        //        topRow = player.getSuggestionsByIndex(1);
        //    }
        //
        //    if (topRow != null) {
        //        int currentIdx = topRow.indexOf(current);
        //        int randomIndex = Utils.getRandomIndex(currentIdx, topRow.getSize());
        //
        //        if (randomIndex != -1) {
        //            Video nextVideo = topRow.get(randomIndex);
        //            current.nextMediaItem = SimpleMediaItem.from(nextVideo);
        //            current.isShuffled = true;
        //            player.setNextTitle(nextVideo);
        //        }
        //    }
        //}
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
}
