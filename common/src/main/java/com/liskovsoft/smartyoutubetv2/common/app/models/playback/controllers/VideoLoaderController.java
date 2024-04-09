package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import android.annotation.SuppressLint;

import com.liskovsoft.mediaserviceinterfaces.yt.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.yt.MotherService;
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
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerUI;
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
import com.liskovsoft.youtubeapi.service.YouTubeMotherService;
import io.reactivex.disposables.Disposable;

import java.util.Collections;
import java.util.List;

public class VideoLoaderController extends PlayerEventListenerHelper implements OnDataChange {
    private static final String TAG = VideoLoaderController.class.getSimpleName();
    private final Playlist mPlaylist;
    private final UniqueRandom mRandom;
    private Video mLastVideo;
    private int mLastError = -1;
    private long mPrevErrorTimeMs;
    private SuggestionsController mSuggestionsController;
    private PlayerData mPlayerData;
    private PlayerTweaksData mPlayerTweaksData;
    private long mSleepTimerStartMs;
    private Disposable mFormatInfoAction;
    private Disposable mMpdStreamAction;
    private final Runnable mReloadVideoHandler = () -> loadVideo(mLastVideo);
    private final Runnable mMetadataSync = () -> {
        if (getPlayer() != null) {
            waitMetadataSync(getPlayer().getVideo(), false);
        }
    };
    private final Runnable mFixAndRestartEngine = () -> {
        if (getPlayer() != null) {
            YouTubeMotherService.instance().invalidateCache();
            getPlayer().restartEngine(); // properly save position of the current track
        }
    };
    private final Runnable mLoadRandomNext = this::loadRandomNext;

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
    public void onEngineInitialized() {
        loadVideo(mLastVideo);
        getPlayer().setRepeatButtonState(mPlayerData.getRepeatMode());
        mSleepTimerStartMs = System.currentTimeMillis();
    }

    @Override
    public void onEngineReleased() {
        disposeActions();
    }

    @Override
    public void onEngineError(int type, int rendererIndex, Throwable error) {
        Log.e(TAG, "Player error occurred: %s. Trying to fix…", type);

        mLastError = type;

        runErrorAction(type, rendererIndex, error);
    }

    @Override
    public void onVideoLoaded(Video video) {
        mLastError = -1;
        getPlayer().setRepeatButtonState(video.finishOnEnded ? PlayerUI.REPEAT_MODE_CLOSE : mPlayerData.getRepeatMode());
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
        Video previous = mPlaylist.getPrevious();

        if (mSuggestionsController.getPrevious() != null) {
            openVideoInt(mSuggestionsController.getPrevious());
        } else if (previous != null) {
            previous.fromQueue = true;
            openVideoInt(previous);
        }
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
            repeatMode = PlayerUI.REPEAT_MODE_CLOSE;
        } else if (video != null && video.isShorts && mPlayerTweaksData.isLoopShortsEnabled()) {
            repeatMode = PlayerUI.REPEAT_MODE_ONE;
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

        MotherService service = YouTubeMotherService.instance();
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
            loadNext();
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
            Utils.postDelayed(mReloadVideoHandler, reloadIntervalMs);
        }
    }

    private boolean isWithinTimeWindow() {
        // Restart once per n seconds
        long currentTimeMillis = System.currentTimeMillis();
        boolean withinTimeWindow = currentTimeMillis - mPrevErrorTimeMs > 10_000;
        mPrevErrorTimeMs = currentTimeMillis;

        return withinTimeWindow;
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
        Utils.removeCallbacks(mReloadVideoHandler, mFixAndRestartEngine, mMetadataSync);
    }

    @SuppressLint("StringFormatMatches")
    private void runErrorAction(int type, int rendererIndex, Throwable error) {
        String message = error != null ? error.getMessage() : null;

        switch (type) {
            // Some ciphered data could be outdated.
            // Might happen when the app wasn't used quite a long time.
            case PlayerEventListener.ERROR_TYPE_SOURCE:
                MessageHelpers.showLongMessage(getContext(), getContext().getString(getSourceErrorResId(rendererIndex)) + "\n" + message);
                break;
            case PlayerEventListener.ERROR_TYPE_RENDERER:
                MessageHelpers.showLongMessage(getContext(), getContext().getString(getRendererErrorResId(rendererIndex)) + "\n" + message);
                applyRendererErrorAction(rendererIndex);
                break;
            // Hide unknown error on all devices
            case PlayerEventListener.ERROR_TYPE_UNEXPECTED:
                // NOP
                break;
            default:
                MessageHelpers.showLongMessage(getContext(), getContext().getString(R.string.msg_player_error, type) + "\n" + message);
                break;
        }

        applyErrorAction(error);

        // Delay to fix frequent requests
        Utils.postDelayed(mFixAndRestartEngine, 3_000);
    }

    private void applyRendererErrorAction(int rendererIndex) {
        switch (rendererIndex) {
            case PlayerEventListener.RENDERER_INDEX_VIDEO:
                FormatItem videoFormat = mPlayerData.getFormat(FormatItem.TYPE_VIDEO);
                if (!videoFormat.isPreset()) {
                    mPlayerData.setFormat(mPlayerData.getDefaultVideoFormat());
                }
                break;
            case PlayerEventListener.RENDERER_INDEX_AUDIO:
                mPlayerData.setFormat(mPlayerData.getDefaultAudioFormat());
                break;
            case PlayerEventListener.RENDERER_INDEX_SUBTITLE:
                mPlayerData.setFormat(FormatItem.SUBTITLE_NONE);
                break;
        }
    }

    private void applyErrorAction(Throwable error) {
        if (error instanceof OutOfMemoryError) {
            mPlayerData.setVideoBufferType(PlayerData.BUFFER_LOW);
        } else if (Helpers.startsWithAny(error.getMessage(), "Unable to connect to ", "Invalid NAL length")) {
            mPlayerTweaksData.setPlayerDataSource(PlayerTweaksData.PLAYER_DATA_SOURCE_DEFAULT);
        }
    }

    private int getSourceErrorResId(int rendererIndex) {
        switch (rendererIndex) {
            case PlayerEventListener.RENDERER_INDEX_VIDEO:
                return R.string.msg_player_error_video_source;
            case PlayerEventListener.RENDERER_INDEX_AUDIO:
                return R.string.msg_player_error_audio_source;
            case PlayerEventListener.RENDERER_INDEX_SUBTITLE:
                return R.string.msg_player_error_subtitle_source;
            default:
                return R.string.msg_player_unknown_error;
        }
    }

    private int getRendererErrorResId(int rendererIndex) {
        switch (rendererIndex) {
            case PlayerEventListener.RENDERER_INDEX_VIDEO:
                return R.string.msg_player_error_video_renderer;
            case PlayerEventListener.RENDERER_INDEX_AUDIO:
                return R.string.msg_player_error_audio_renderer;
            case PlayerEventListener.RENDERER_INDEX_SUBTITLE:
                return R.string.msg_player_error_subtitle_renderer;
            default:
                return R.string.msg_player_unknown_error;
        }
    }

    private List<String> applyFix(List<String> urlList) {
        // Sometimes top url cannot be played
        if (mLastError == PlayerEventListener.ERROR_TYPE_SOURCE) {
            Collections.reverse(urlList);
        }

        return urlList;
    }

    private void applyRepeatMode(int repeatMode) {
        // Fix simultaneous videos loading (e.g. when playback ends and user opens new video)
        if (isActionsRunning()) {
            return;
        }

        switch (repeatMode) {
            case PlayerUI.REPEAT_MODE_ALL:
            case PlayerUI.REPEAT_MODE_SHUFFLE:
                loadNext();
                break;
            case PlayerUI.REPEAT_MODE_ONE:
                getPlayer().setPositionMs(100); // fix frozen image on Android 4?
                break;
            case PlayerUI.REPEAT_MODE_CLOSE:
                // Close player if suggestions not shown
                // Except when playing from queue
                if (mPlaylist.getNext() != null) {
                    loadNext();
                } else if (!getPlayer().isSuggestionsShown() && !AppDialogPresenter.instance(getContext()).isDialogShown()) {
                    getPlayer().finishReally();
                }
                break;
            case PlayerUI.REPEAT_MODE_PAUSE:
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
            case PlayerUI.REPEAT_MODE_LIST:
                // stop player (if not playing playlist)
                Video video = getPlayer().getVideo();
                if ((video != null && video.hasNextPlaylist()) || mPlaylist.getNext() != null) {
                    loadNext();
                } else {
                    getPlayer().setPositionMs(getPlayer().getDurationMs());
                    getPlayer().setPlayWhenReady(false);
                    getPlayer().showSuggestions(true);
                }
                break;
        }

        Log.e(TAG, "Undetected repeat mode " + repeatMode);
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
    public void onDataChange() {
        Utils.postDelayed(mLoadRandomNext, 3_000);
    }

    private void loadRandomNext() {
        MediaServiceManager.instance().disposeActions();

        if (getPlayer() == null || mPlayerData == null || mLastVideo == null || mLastVideo.playlistInfo == null) {
            return;
        }

        if (mPlayerData.getRepeatMode() == PlayerUI.REPEAT_MODE_SHUFFLE) {
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
}
