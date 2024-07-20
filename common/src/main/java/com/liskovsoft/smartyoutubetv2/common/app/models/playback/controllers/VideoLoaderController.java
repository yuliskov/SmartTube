package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import android.annotation.SuppressLint;

import com.liskovsoft.mediaserviceinterfaces.yt.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.yt.ServiceManager;
import com.liskovsoft.mediaserviceinterfaces.yt.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.yt.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SampleMediaItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerEngineConstants;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.VideoActionPresenter;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataChangeBase.OnDataChange;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.UniqueRandom;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import io.reactivex.disposables.Disposable;

import java.util.Collections;
import java.util.List;

public class VideoLoaderController extends PlayerEventListenerHelper implements OnDataChange {
    private static final String TAG = VideoLoaderController.class.getSimpleName();
    private static final long STREAM_END_THRESHOLD_MS = 180_000;
    private final Playlist mPlaylist;
    private final UniqueRandom mRandom;
    private Video mLastVideo;
    private int mLastErrorType = -1;
    private long mLastErrorTimeMs;
    private int mErrorCount;
    private SuggestionsController mSuggestionsController;
    private PlayerData mPlayerData;
    private PlayerTweaksData mPlayerTweaksData;
    private long mSleepTimerStartMs;
    private Disposable mFormatInfoAction;
    private Disposable mMpdStreamAction;
    private final Runnable mReloadVideo = () -> loadVideo(mLastVideo);
    private final Runnable mLoadNext = this::loadNext;
    private final Runnable mMetadataSync = () -> {
        if (getPlayer() != null) {
            waitMetadataSync(getPlayer().getVideo(), false);
        }
    };
    private final Runnable mFixAndRestartEngine = () -> {
        if (getPlayer() != null) {
            YouTubeServiceManager.instance().invalidateCache();
            getPlayer().restartEngine(); // properly save position of the current track
        }
    };
    private final Runnable mLoadRandomNext = this::loadRandomNext;
    private final Runnable mOnLongBuffering = this::onLongBuffering;

    public VideoLoaderController() {
        mPlaylist = Playlist.instance();
        mRandom = new UniqueRandom();
    }

    @Override
    public void onInit() {
        mSuggestionsController = getController(SuggestionsController.class);
        mPlayerData = PlayerData.instance(getContext());
        mPlayerData.setOnChange(this);
        mPlayerTweaksData = PlayerTweaksData.instance(getContext());
        mSleepTimerStartMs = System.currentTimeMillis();
    }

    @Override
    public void openVideo(Video item) {
        if (item == null) {
            return;
        }

        boolean isVideoChanged = !item.equals(mLastVideo);
        mLastVideo = item; // save for later

        if (!item.fromQueue) {
            mPlaylist.add(item);
        } else {
            item.fromQueue = false;
        }

        if (getPlayer() != null && getPlayer().isEngineInitialized()) { // player is initialized
            if (isVideoChanged) {
                loadVideo(item); // force play immediately
            } else {
                loadSuggestions(item); // update suggestions only
            }
        }
    }

    @Override
    public void onBuffering() {
        Utils.postDelayed(mOnLongBuffering, 10_000);
    }

    private void onLongBuffering() {
        if (mLastVideo == null) {
            return;
        }

        // Stream end check (hangs on buffering)
        if ((!mLastVideo.isLive || mLastVideo.isLiveEnd) &&
                getPlayer().getDurationMs() - getPlayer().getPositionMs() < STREAM_END_THRESHOLD_MS) {
            getMainController().onPlayEnd();
        } else {
            updateErrorCounter(-1);
            if (mErrorCount > 2) {
                YouTubeServiceManager.instance().applyVideoInfoFix();
            }
        }
    }

    @Override
    public void onEngineInitialized() {
        loadVideo(mLastVideo);
        getPlayer().setButtonState(R.id.action_repeat, mPlayerData.getRepeatMode());
        mSleepTimerStartMs = System.currentTimeMillis();
    }

    @Override
    public void onEngineReleased() {
        disposeActions();
    }

    @Override
    public void onEngineError(int type, int rendererIndex, Throwable error) {
        Log.e(TAG, "Player error occurred: %s. Trying to fix…", type);

        updateErrorCounter(type);
        runErrorAction(type, rendererIndex, error);
    }

    @Override
    public void onVideoLoaded(Video video) {
        mLastErrorType = -1;
        Utils.removeCallbacks(mOnLongBuffering);
        getPlayer().setButtonState(R.id.action_repeat, video.finishOnEnded ? PlayerEngineConstants.REPEAT_MODE_CLOSE : mPlayerData.getRepeatMode());
    }

    @Override
    public boolean onPreviousClicked() {
        loadPrevious();

        return true;
    }

    @Override
    public boolean onNextClicked() {
        if (GeneralData.instance(getContext()).isChildModeEnabled()) {
            onPlayEnd();
        } else {
            loadNext();
        }

        return true;
    }

    @Override
    public void onFinish() {
        mPlaylist.clearPosition();
    }

    public void loadPrevious() {
        openVideoInt(mSuggestionsController.getPrevious());
    }

    public void loadNext() {
        Video next = mSuggestionsController.getNext();
        mLastVideo = null; // in case next video is the same as previous

        if (next != null) {
            openVideoInt(next);
        } else {
            waitMetadataSync(getPlayer().getVideo(), true);
        }

        if (mPlayerTweaksData.isPlayerUiOnNextEnabled()) {
            getPlayer().showOverlay(true);
        }
    }

    @Override
    public void onPlayEnd() {
        int repeatMode = mPlayerData.getRepeatMode();

        Video video = getPlayer().getVideo();
        if (video != null && video.finishOnEnded) {
            repeatMode = PlayerEngineConstants.REPEAT_MODE_CLOSE;
        } else if (video != null && video.isShorts && mPlayerTweaksData.isLoopShortsEnabled()) {
            repeatMode = PlayerEngineConstants.REPEAT_MODE_ONE;
        }

        applyRepeatMode(repeatMode);
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
        mSleepTimerStartMs = System.currentTimeMillis();

        // Remove error msg if needed
        if (mPlayerData.isSonyTimerFixEnabled()) {
            getPlayer().setVideo(mLastVideo);
        }

        Utils.removeCallbacks(mFixAndRestartEngine);
        Utils.removeCallbacks(mOnLongBuffering);

        return false;
    }

    @Override
    public void onTickle() {
        checkSleepTimer();
    }

    private void checkSleepTimer() {
        if (mPlayerData.isSonyTimerFixEnabled() && System.currentTimeMillis() - mSleepTimerStartMs > 60 * 60 * 1_000) {
            getPlayer().setPlayWhenReady(false);
            getPlayer().setTitle(getContext().getString(R.string.sleep_timer));
            getPlayer().showOverlay(true);
        }
    }

    /**
     * Force load and play!
     */
    private void loadVideo(Video item) {
        if (item != null) {
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
        disposeActions();

        ServiceManager service = YouTubeServiceManager.instance();
        MediaItemService mediaItemManager = service.getMediaItemService();
        mFormatInfoAction = mediaItemManager.getFormatInfoObserve(video.videoId)
                .subscribe(this::processFormatInfo,
                           error -> {
                               Log.e(TAG, "loadFormatInfo error: %s", error.getMessage());
                               Log.e(TAG, "Probably no internet connection");
                               scheduleReloadVideoTimer(1_000);
                           });
    }

    private void processFormatInfo(MediaItemFormatInfo formatInfo) {
        String bgImageUrl = null;

        mLastVideo.sync(formatInfo);

        if (formatInfo.isUnplayable()) {
            getPlayer().setTitle(formatInfo.getPlayabilityStatus());
            getPlayer().showOverlay(true);
            mSuggestionsController.loadSuggestions(mLastVideo);
            bgImageUrl = mLastVideo.getBackgroundUrl();
            Utils.postDelayed(mLoadNext, 5_000);
        } else if (formatInfo.containsDashVideoInfo() && acceptDashVideoInfo(formatInfo)) {
            Log.d(TAG, "Found regular video in dash format. Loading...");

            mMpdStreamAction = formatInfo.createMpdStreamObservable()
                    .subscribe(
                            dashManifest -> {
                                if (mPlayerTweaksData.isHighBitrateFormatsUnlocked() && formatInfo.hasExtendedHlsFormats()) {
                                    getPlayer().openMerged(dashManifest, formatInfo.getHlsManifestUrl());
                                } else {
                                    getPlayer().openDash(dashManifest);
                                }
                            },
                            error -> Log.e(TAG, "createMpdStream error: %s", error.getMessage())
                    );
        } else if (formatInfo.isLive() && formatInfo.containsDashUrl() && acceptDashUrl(formatInfo)) {
            Log.d(TAG, "Found live video (current or past live stream) in dash format. Loading...");
            getPlayer().openDashUrl(formatInfo.getDashManifestUrl());
        } else if (formatInfo.isLive() && formatInfo.containsHlsUrl()) {
            Log.d(TAG, "Found live video (current or past live stream) in hls format. Loading...");
            getPlayer().openHlsUrl(formatInfo.getHlsManifestUrl());
        } else if (formatInfo.containsUrlListInfo()) {
            Log.d(TAG, "Found url list video. This is always LQ. Loading...");
            getPlayer().openUrlList(applyFix(formatInfo.createUrlList()));
        } else {
            Log.d(TAG, "Empty format info received. Seems future live translation. No video data to pass to the player.");
            scheduleReloadVideoTimer(30 * 1_000);
            mSuggestionsController.loadSuggestions(mLastVideo);
            bgImageUrl = mLastVideo.getBackgroundUrl();
        }

        getPlayer().showBackground(bgImageUrl);

        if (bgImageUrl != null && getPlayer().containsMedia()) {
            // Make background visible
            getPlayer().restartEngine();
        }
    }

    private void scheduleReloadVideoTimer(int reloadIntervalMs) {
        if (getPlayer().isEngineInitialized()) {
            Log.d(TAG, "Starting check for the future stream...");
            getPlayer().showOverlay(true);
            Utils.postDelayed(mReloadVideo, reloadIntervalMs);
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
            getMainController().openVideo(item);
            //getPlayer().showOverlay(true);
        } else {
            VideoActionPresenter.instance(getContext()).apply(item);
        }
    }

    private boolean isActionsRunning() {
        return RxHelper.isAnyActionRunning(mFormatInfoAction, mMpdStreamAction);
    }

    private void disposeActions() {
        MediaServiceManager.instance().disposeActions();
        RxHelper.disposeActions(mFormatInfoAction, mMpdStreamAction);
        Utils.removeCallbacks(mReloadVideo, mLoadNext, mFixAndRestartEngine, mMetadataSync);
        Utils.removeCallbacks(mOnLongBuffering);
    }

    private void updateErrorCounter(int type) {
        long currentTimeMillis = System.currentTimeMillis();
        if (currentTimeMillis - mLastErrorTimeMs < 60_000 && mLastErrorType == type) {
            mErrorCount++;
        } else {
            mErrorCount = 1;
        }

        mLastErrorType = type;
        mLastErrorTimeMs = currentTimeMillis;
    }

    @SuppressLint("StringFormatMatches")
    private void runErrorAction(int type, int rendererIndex, Throwable error) {
        String message = error != null ? error.getMessage() : null;

        switch (type) {
            // Some ciphered data could be outdated.
            // Might happen when the app wasn't used quite a long time.
            case PlayerEventListener.ERROR_TYPE_SOURCE:
                applySourceErrorAction(rendererIndex, error);
                break;
            case PlayerEventListener.ERROR_TYPE_RENDERER:
                applyRendererErrorAction(rendererIndex, error);
                break;
            // Hide unknown error on all devices
            case PlayerEventListener.ERROR_TYPE_UNEXPECTED:
                // NOP
                break;
            default:
                MessageHelpers.showLongMessage(getContext(), getContext().getString(R.string.msg_player_error, type) + "\n" + message);
                break;
        }

        applyGenericErrorAction(error);

        restartEngine();
    }

    private void applySourceErrorAction(int rendererIndex, Throwable error) {
        String message = error != null ? error.getMessage() : null;
        int msgResId;

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

        MessageHelpers.showLongMessage(getContext(), getContext().getString(msgResId) + "\n" + message);
    }

    private void applyRendererErrorAction(int rendererIndex, Throwable error) {
        String message = error != null ? error.getMessage() : null;
        int msgResId;

        switch (rendererIndex) {
            case PlayerEventListener.RENDERER_INDEX_VIDEO:
                msgResId = R.string.msg_player_error_video_renderer;
                FormatItem videoFormat = mPlayerData.getFormat(FormatItem.TYPE_VIDEO);
                if (!videoFormat.isPreset()) {
                    mPlayerData.setFormat(mPlayerData.getDefaultVideoFormat());
                }
                mPlayerTweaksData.forceSWDecoder(false);
                break;
            case PlayerEventListener.RENDERER_INDEX_AUDIO:
                msgResId = R.string.msg_player_error_audio_renderer;
                mPlayerData.setFormat(mPlayerData.getDefaultAudioFormat());
                break;
            case PlayerEventListener.RENDERER_INDEX_SUBTITLE:
                msgResId = R.string.msg_player_error_subtitle_renderer;
                mPlayerData.setFormat(FormatItem.SUBTITLE_NONE);
                break;
            default:
                msgResId = R.string.unknown_renderer_error;
        }

        MessageHelpers.showLongMessage(getContext(), getContext().getString(msgResId) + "\n" + message);
    }

    private void applyGenericErrorAction(Throwable error) {
        if (error instanceof OutOfMemoryError) {
            if (mPlayerData.getVideoBufferType() == PlayerData.BUFFER_LOW) {
                mPlayerTweaksData.enableSectionPlaylist(false);
            } else {
                mPlayerData.setVideoBufferType(PlayerData.BUFFER_LOW);
            }
        } else if (Helpers.startsWithAny(error.getMessage(),
                "Unable to connect to", "Invalid NAL length", "Response code: 421", "Invalid integer size")) {
            // Switch between network engines in hope that one of them fixes the error
            //mPlayerTweaksData.setPlayerDataSource(getNextEngine());
            YouTubeServiceManager.instance().applyVideoInfoFix();
        } else if (Helpers.startsWithAny(error.getMessage(), "Response code: 403")) {
            // "Response code: 403" is related to outdated VISITOR_INFO1_LIVE cookie
            YouTubeServiceManager.instance().applyVideoInfoFix();
        }
    }

    private void restartEngine() {
        // Give a time to user to do something
        Utils.postDelayed(mFixAndRestartEngine, 5_000);
    }

    private List<String> applyFix(List<String> urlList) {
        // Sometimes top url cannot be played
        if (mLastErrorType == PlayerEventListener.ERROR_TYPE_SOURCE) {
            Collections.reverse(urlList);
        }

        return urlList;
    }

    private void applyRepeatMode(int repeatMode) {
        Video video = getPlayer().getVideo();
        // Fix simultaneous videos loading (e.g. when playback ends and user opens new video)
        if (isActionsRunning() || video == null) {
            return;
        }

        switch (repeatMode) {
            case PlayerEngineConstants.REPEAT_MODE_REVERSE_LIST:
                if (video.hasPlaylist() || video.belongsToChannelUploads() || video.belongsToChannel()) {
                    VideoGroup group = video.getGroup();
                    if (group != null && group.indexOf(video) != 0) { // stop after first
                        onPreviousClicked();
                    }
                    break;
                }
            case PlayerEngineConstants.REPEAT_MODE_ALL:
            case PlayerEngineConstants.REPEAT_MODE_SHUFFLE:
                loadNext();
                break;
            case PlayerEngineConstants.REPEAT_MODE_ONE:
                getPlayer().setPositionMs(100); // fix frozen image on Android 4?
                break;
            case PlayerEngineConstants.REPEAT_MODE_CLOSE:
                // Close player if suggestions not shown
                // Except when playing from queue
                if (mPlaylist.getNext() != null) {
                    loadNext();
                } else {
                    AppDialogPresenter dialog = AppDialogPresenter.instance(getContext());
                    if (!getPlayer().isSuggestionsShown() && (!dialog.isDialogShown() || dialog.isTransparent())) {
                        dialog.closeDialog();
                        getPlayer().finishReally();
                    }
                }
                break;
            case PlayerEngineConstants.REPEAT_MODE_PAUSE:
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
            case PlayerEngineConstants.REPEAT_MODE_LIST:
                // stop player (if not playing playlist)
                if (video.hasNextPlaylist() || mPlaylist.getNext() != null) {
                    loadNext();
                } else {
                    getPlayer().setPositionMs(getPlayer().getDurationMs());
                    getPlayer().setPlayWhenReady(false);
                    getPlayer().showSuggestions(true);
                }
                break;
            default:
                Log.e(TAG, "Undetected repeat mode " + repeatMode);
                break;
        }
    }

    private boolean acceptDashVideoInfo(MediaItemFormatInfo formatInfo) {
        // Not enough info for full length live streams
        if (formatInfo.isLive() && formatInfo.getStartTimeMs() == 0) {
            return false;
        }

        // Live dash url doesn't work with None buffer
        if (formatInfo.isLive() && (mPlayerTweaksData.isDashUrlStreamsForced() || mPlayerData.getVideoBufferType() == PlayerData.BUFFER_NONE)) {
            return false;
        }

        if (formatInfo.isLive() && mPlayerTweaksData.isHlsStreamsForced()) {
            return false;
        }

        if (mPlayerData.isLegacyCodecsForced()) {
            return false;
        }

        return true;
    }

    private boolean acceptDashUrl(MediaItemFormatInfo formatInfo) {
        if (formatInfo.isLive() && mPlayerTweaksData.isHlsStreamsForced() && formatInfo.containsHlsUrl()) {
            return false;
        }

        if (mPlayerData.isLegacyCodecsForced()) {
            return false;
        }

        return true;
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
    public void onDataChange() {
        Utils.postDelayed(mLoadRandomNext, 3_000);
    }

    private void loadRandomNext() {
        MediaServiceManager.instance().disposeActions();

        if (getPlayer() == null || mPlayerData == null || mLastVideo == null || mLastVideo.playlistInfo == null) {
            return;
        }

        if (mPlayerData.getRepeatMode() == PlayerEngineConstants.REPEAT_MODE_SHUFFLE) {
            Video video = new Video();
            video.playlistId = mLastVideo.playlistId;
            VideoGroup topRow = getPlayer().getSuggestionsByIndex(0);
            video.playlistIndex = mRandom.getPlaylistIndex(mLastVideo.getPlaylistId(),
                    mLastVideo.playlistInfo.getSize() != -1 ? mLastVideo.playlistInfo.getSize() : topRow != null ? topRow.getVideos().size() : -1);

            MediaServiceManager.instance().loadMetadata(video, randomMetadata -> {
                if (randomMetadata.getNextVideo() == null) {
                    return;
                }

                if (mLastVideo.nextMediaItemBackup == null) {
                    mLastVideo.nextMediaItemBackup = mLastVideo.nextMediaItem;
                }

                mLastVideo.nextMediaItem = SampleMediaItem.from(randomMetadata);
                getPlayer().setNextTitle(mLastVideo.nextMediaItem.getTitle());
            });
        } else if (mLastVideo.nextMediaItemBackup != null) {
            mLastVideo.nextMediaItem = mLastVideo.nextMediaItemBackup;
            getPlayer().setNextTitle(mLastVideo.nextMediaItem.getTitle());
        }
    }

    private int getNextEngine() {
        int currentEngine = mPlayerTweaksData.getPlayerDataSource();
        int[] engineList = { PlayerTweaksData.PLAYER_DATA_SOURCE_CRONET, PlayerTweaksData.PLAYER_DATA_SOURCE_DEFAULT, PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP };
        return Helpers.getNextValue(currentEngine, engineList);
    }
}
