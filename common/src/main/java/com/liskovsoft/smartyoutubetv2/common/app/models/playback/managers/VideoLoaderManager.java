package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import android.os.Handler;
import android.os.Looper;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.sharedutils.Analytics;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SampleMediaItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackUIController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.SuggestionsLoaderManager.MetadataListener;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.DataChangeBase.OnDataChange;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.UniqueRandom;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VideoLoaderManager extends PlayerEventListenerHelper implements MetadataListener, OnDataChange {
    private static final String TAG = VideoLoaderManager.class.getSimpleName();
    private final Playlist mPlaylist;
    private final Handler mHandler;
    private final SuggestionsLoaderManager mSuggestionsLoader;
    private final UniqueRandom mRandom;
    private Video mLastVideo;
    private int mLastError = -1;
    private long mPrevErrorTimeMs;
    private PlayerData mPlayerData;
    private PlayerTweaksData mPlayerTweaksData;
    private long mSleepTimerStartMs;
    private Disposable mFormatInfoAction;
    private Disposable mMpdStreamAction;
    private final Map<Integer, Runnable> mErrorActions = new HashMap<>();
    private final Runnable mReloadVideoHandler = () -> loadVideo(mLastVideo);
    private final Runnable mPendingNext = () -> {
        if (getController() != null) {
            openVideoFromNext(getController().getVideo(), false);
        }
    };
    private final Runnable mPendingRestartEngine = () -> {
        if (getController() != null) {
            YouTubeMediaService.instance().invalidateCache();
            getController().restartEngine(); // properly save position of the current track
        }
    };
    private final Runnable mLoadRandomNext = this::loadRandomNext;
    private boolean mIsWasVideoStartError;
    private boolean mIsWasStarted;

    public VideoLoaderManager(SuggestionsLoaderManager suggestionsLoader) {
        mSuggestionsLoader = suggestionsLoader;
        mPlaylist = Playlist.instance();
        mHandler = new Handler(Looper.getMainLooper());
        mRandom = new UniqueRandom();
    }

    @Override
    public void onInitDone() {
        mPlayerData = PlayerData.instance(getActivity());
        mPlayerData.setOnChange(this);
        mPlayerTweaksData = PlayerTweaksData.instance(getActivity());
        initErrorActions();
    }

    @Override
    public void openVideo(Video item) {
        if (item == null) {
            return;
        }

        if (!item.fromQueue) {
            mPlaylist.add(item);
        } else {
            item.fromQueue = false;
        }

        if (getController() != null && getController().isEngineInitialized()) { // player is initialized
            if (!item.equals(mLastVideo)) {
                loadVideo(item); // force play immediately
            } else {
                loadSuggestions(item);
            }
        } else {
            mLastVideo = item; // save for later
        }
        mIsWasVideoStartError = false;
        mIsWasStarted = false;
        Analytics.sendVideoStarting(item.videoId, item.title);
    }

    @Override
    public void onEngineInitialized() {
        loadVideo(mLastVideo);
        getController().setRepeatButtonState(mPlayerData.getRepeatMode());
        mSleepTimerStartMs = System.currentTimeMillis();
    }

    @Override
    public void onEngineReleased() {
        disposeActions();
    }

    @Override
    public void onEngineError(int error) {
        Log.e(TAG, "Player error occurred: %s. Trying to fix…", error);

        mLastError = error;
        startErrorAction(error);
        if (!mIsWasVideoStartError && mLastVideo != null) {
            Analytics.sendVideoStartError(mLastVideo.videoId,
                    mLastVideo.title,
                    Integer.toString(error));
            mIsWasVideoStartError = true;
        }
    }

    @Override
    public void onVideoLoaded(Video video) {
        mLastError = -1;
        getController().setRepeatButtonState(video.finishOnEnded ? PlaybackUIController.REPEAT_MODE_CLOSE : mPlayerData.getRepeatMode());
    }

    @Override
    public void onPlay() {
        //MessageHelpers.showMessage(getActivity(), "Start playing!");

        if (!mIsWasStarted && mLastVideo != null) {
            Analytics.sendVideoStarted(mLastVideo.videoId, mLastVideo.title);
            mIsWasStarted = true;
        }
    }

    public boolean onPreviousClicked() {
        loadPrevious();

        return true;
    }

    @Override
    public boolean onNextClicked() {
        loadNext();

        return true;
    }

    @Override
    public void onFinish() {
        mPlaylist.clearPosition();
    }

    public void loadPrevious() {
        Video previous = mPlaylist.getPrevious();

        if (previous != null) {
            previous.fromQueue = true;
            openVideoInt(previous);
        }
    }

    public void loadNext() {
        Video next = mPlaylist.getNext();
        mLastVideo = null; // in case next video is the same as previous

        if (next == null) {
            openVideoFromNext(getController().getVideo(), true);
        } else {
            next.fromQueue = true;
            openVideoInt(next);
        }
    }

    @Override
    public void onPlayEnd() {
        int repeatMode = checkSleepTimer(mPlayerData.getRepeatMode());

        Video video = getController().getVideo();
        if (video != null && video.finishOnEnded) {
            repeatMode = PlaybackUIController.REPEAT_MODE_CLOSE;
        }

        applyRepeatMode(repeatMode);
    }

    @Override
    public void onSuggestionItemClicked(Video item) {
        openVideoInt(item);
    }

    @Override
    public void onPlaybackQueueClicked() {
        AppDialogUtil.showPlaybackQueueDialog(getActivity(), this::openVideoInt);
    }

    @Override
    public boolean onKeyDown(int keyCode) {
        mSleepTimerStartMs = System.currentTimeMillis();

        return false;
    }

    private int checkSleepTimer(int playbackMode) {
        if (mPlayerData.isSonyTimerFixEnabled() && System.currentTimeMillis() - mSleepTimerStartMs > 60 * 60 * 1_000) {
            playbackMode = PlaybackUIController.REPEAT_MODE_PAUSE;
            getController().showError(getActivity().getString(R.string.sleep_timer));
        }

        return playbackMode;
    }

    /**
     * Force load and play!
     */
    private void loadVideo(Video item) {
        if (item != null) {
            mPlaylist.setCurrent(item);
            mLastVideo = item;
            getController().setVideo(item);
            getController().resetPlayerState();
            loadFormatInfo(item);
        }
    }

    /**
     * Force load suggestions.
     */
    private void loadSuggestions(Video item) {
        if (item != null) {
            mPlaylist.setCurrent(item);
            mLastVideo = item;
            getController().setVideo(item);
            mSuggestionsLoader.loadSuggestions(item);
        }
    }

    private void openVideoFromNext(Video current, boolean showLoadingMsg) {
        if (current == null) {
            return;
        }

        // Significantly improves next video loading time!
        if (current.nextMediaItem != null) {
            openVideoInt(Video.from(current.nextMediaItem));
        } else if (!current.isSynced) { // Maybe there's nothing left. E.g. when casting from phone
            // Wait in a loop while suggestions have been loaded...
            if (showLoadingMsg) {
                MessageHelpers.showMessageThrottled(getActivity(), R.string.wait_data_loading);
            }
            // Short videos next fix (suggestions aren't loaded yet)
            boolean isEnded = getController() != null && Math.abs(getController().getDurationMs() - getController().getPositionMs()) < 100;
            if (isEnded) {
                Utils.postDelayed(mHandler, mPendingNext, 1_000);
            }
        }
    }

    private void loadFormatInfo(Video video) {
        disposeActions();

        MediaService service = YouTubeMediaService.instance();
        service.enableOldStreams(mPlayerTweaksData.isLiveStreamFixEnabled());
        MediaItemService mediaItemManager = service.getMediaItemService();
        mFormatInfoAction = mediaItemManager.getFormatInfoObserve(video.videoId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::processFormatInfo,
                           error -> {
                               Log.e(TAG, "loadFormatInfo error: %s", error.getMessage());
                               scheduleReloadVideoTimer(1_000);
                               if (!mIsWasVideoStartError) {
                                   Analytics.sendVideoStartError(video.videoId,
                                           video.title,
                                           error.getMessage());
                                   mIsWasVideoStartError = true;
                               }
                           });
    }

    private void processFormatInfo(MediaItemFormatInfo formatInfo) {
        String bgImageUrl = null;

        mLastVideo.sync(formatInfo);

        if (formatInfo.isUnplayable() || formatInfo.isAgeRestricted()) {
            getController().showError(formatInfo.getPlayabilityStatus());
            bgImageUrl = mLastVideo.getBackgroundUrl();
            if (!mIsWasVideoStartError) {
                Analytics.sendVideoStartError(mLastVideo.videoId,
                        mLastVideo.title,
                        formatInfo.getPlayabilityStatus());
                mIsWasVideoStartError = true;
            }
            if (formatInfo.isAgeRestricted()) {
                SignInPresenter.instance(getActivity()).start();
                getController().finish();
            }
        } else if (formatInfo.containsDashVideoInfo() && !forceLegacyFormat(formatInfo)) {
            Log.d(TAG, "Found regular video in dash format. Loading...");

            mMpdStreamAction = formatInfo.createMpdStreamObservable()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            dashManifest -> getController().openDash(dashManifest),
                            error -> Log.e(TAG, "createMpdStream error: %s", error.getMessage())
                    );
        } else if (formatInfo.isLive() && formatInfo.containsDashUrl() && !forceLegacyFormat(formatInfo)) {
            Log.d(TAG, "Found live video (current or past live stream) in dash format. Loading...");
            getController().openDashUrl(formatInfo.getDashManifestUrl());
        } else if (formatInfo.isLive() && formatInfo.containsHlsUrl() && forceLegacyFormat(formatInfo)) {
            Log.d(TAG, "Found live video (current or past live stream) in hls format. Loading...");
            getController().openHlsUrl(formatInfo.getHlsManifestUrl());
        } else if (formatInfo.containsUrlListInfo()) {
            Log.d(TAG, "Found url list video. This is always LQ. Loading...");
            getController().openUrlList(applyFix(formatInfo.createUrlList()));
        } else {
            Log.d(TAG, "Empty format info received. Seems future live translation. No video data to pass to the player.");
            scheduleReloadVideoTimer(30 * 1_000);
            mSuggestionsLoader.loadSuggestions(mLastVideo);
            bgImageUrl = mLastVideo.getBackgroundUrl();
            if (!mIsWasVideoStartError) {
                Analytics.sendVideoStartError(mLastVideo.videoId,
                        mLastVideo.title,
                        formatInfo.getPlayabilityStatus());
                mIsWasVideoStartError = true;
            }
        }

        getController().showBackground(bgImageUrl);

        if (bgImageUrl != null && getController().containsMedia()) {
            // Make background visible
            getController().restartEngine();
        }
    }

    private void scheduleReloadVideoTimer(int reloadIntervalMs) {
        if (getController().isEngineInitialized()) {
            Log.d(TAG, "Starting check for the future stream...");
            getController().showOverlay(true);
            Utils.postDelayed(mHandler, mReloadVideoHandler, reloadIntervalMs);
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
            getController().showOverlay(true);
            getBridge().openVideo(item);
        } else if (item.hasChannel()) {
            ChannelPresenter.instance(getActivity()).openChannel(item);
        } else {
            Log.e(TAG, "Video item doesn't contain needed data!");
        }
    }

    private boolean isActionsRunning() {
        return RxUtils.isAnyActionRunning(mFormatInfoAction, mMpdStreamAction);
    }

    private void disposeActions() {
        MediaServiceManager.instance().disposeActions();
        RxUtils.disposeActions(mFormatInfoAction, mMpdStreamAction);
        Utils.removeCallbacks(mHandler, mReloadVideoHandler, mPendingRestartEngine, mPendingNext);
    }

    private void initErrorActions() {
        // Some ciphered data could be outdated.
        // Might happen when the app wasn't used quite a long time.
        mErrorActions.put(PlayerEventListener.ERROR_TYPE_SOURCE, () -> {
            // This buffering setting could also cause such errors.
            if (mPlayerTweaksData.isBufferingFixEnabled()) {
                mPlayerTweaksData.enableBufferingFix(false);
            }

            MessageHelpers.showMessage(getActivity(), R.string.msg_player_error_source2);
        });
        mErrorActions.put(PlayerEventListener.ERROR_TYPE_RENDERER, () -> MessageHelpers.showMessage(getActivity(), R.string.msg_player_error_renderer));

        // Hide unknown error on stable build only
        //mErrorMap.put(PlayerEventListener.ERROR_TYPE_UNEXPECTED, BuildConfig.FLAVOR.equals("ststable") ? -1 : R.string.msg_player_error_unexpected);

        // Hide unknown error on all devices
        mErrorActions.put(PlayerEventListener.ERROR_TYPE_UNEXPECTED, () -> {});
    }

    private void startErrorAction(int error) {
        Runnable action = mErrorActions.get(error);

        if (action != null) {
            action.run();
        } else {
            MessageHelpers.showMessage(getActivity(), getActivity().getString(R.string.msg_player_error, error));
        }

        // Delay to fix frequent requests
        Utils.postDelayed(mHandler, mPendingRestartEngine, 3_000);
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
            case PlaybackUIController.REPEAT_MODE_ALL:
            case PlaybackUIController.REPEAT_MODE_SHUFFLE:
                loadNext();
                getController().showOverlay(true);
                break;
            case PlaybackUIController.REPEAT_MODE_ONE:
                getController().setPositionMs(0);
                getController().setPlayWhenReady(true);
                Utils.showRepeatInfo(getActivity(), repeatMode);
                break;
            case PlaybackUIController.REPEAT_MODE_CLOSE:
                // Close player if suggestions not shown
                // Except when playing from queue
                if (mPlaylist.getNext() != null) {
                    loadNext();
                    getController().showOverlay(true);
                } else if (!getController().isSuggestionsShown()) {
                    getController().finishReally();
                }
                break;
            case PlaybackUIController.REPEAT_MODE_PAUSE:
                // Stop player after each video.
                // Except when playing from queue
                if (mPlaylist.getNext() != null) {
                    loadNext();
                    getController().showOverlay(true);
                } else {
                    getController().showSuggestions(true);
                    getController().setPlayWhenReady(false);
                    getController().setPositionMs(0);
                    Utils.showRepeatInfo(getActivity(), repeatMode);
                }
                break;
            case PlaybackUIController.REPEAT_MODE_LIST:
                // stop player (if not playing playlist)
                Video video = getController().getVideo();
                if ((video != null && video.hasPlaylist()) || mPlaylist.getNext() != null) {
                    loadNext();
                    getController().showOverlay(true);
                } else {
                    getController().showSuggestions(true);
                    getController().setPlayWhenReady(false);
                    getController().setPositionMs(0);
                    Utils.showRepeatInfo(getActivity(), repeatMode);
                }
                break;
        }

        Log.e(TAG, "Undetected repeat mode " + repeatMode);
    }

    private boolean forceLegacyFormat(MediaItemFormatInfo formatInfo) {
        boolean isLive = formatInfo.isLive() || formatInfo.isLiveContent();

        if (isLive && mPlayerTweaksData.isLiveStreamFixEnabled() && formatInfo.containsHlsUrl()) {
            return true;
        }

        if (!isLive && mPlayerData.isLegacyCodecsForced()) {
            return true;
        }

        return false;
    }

    @Override
    public void onMetadata(MediaItemMetadata metadata) {
        loadRandomNext();
    }

    @Override
    public void onDataChange() {
        Utils.postDelayed(mHandler, mLoadRandomNext, 3_000);
    }

    private void loadRandomNext() {
        MediaServiceManager.instance().disposeActions();

        if (getController() == null || mPlayerData == null || mLastVideo == null || mLastVideo.playlistInfo == null) {
            return;
        }

        if (mPlayerData.getRepeatMode() == PlaybackUIController.REPEAT_MODE_SHUFFLE) {
            Video video = new Video();
            video.playlistId = mLastVideo.playlistId;
            VideoGroup topRow = getController().getSuggestionsByIndex(0);
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
                getController().setNextTitle(mLastVideo.nextMediaItem.getTitle());
            });
        } else if (mLastVideo.nextMediaItemBackup != null) {
            mLastVideo.nextMediaItem = mLastVideo.nextMediaItemBackup;
            getController().setNextTitle(mLastVideo.nextMediaItem.getTitle());
        }
    }
}
