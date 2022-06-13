package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import android.os.Handler;
import android.os.Looper;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackEngineController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VideoLoaderManager extends PlayerEventListenerHelper {
    private static final String TAG = VideoLoaderManager.class.getSimpleName();
    private final Playlist mPlaylist;
    private final Handler mHandler;
    private final SuggestionsLoaderManager mSuggestionsLoader;
    private Video mLastVideo;
    private int mLastError = -1;
    private long mPrevErrorTimeMs;
    private PlayerData mPlayerData;
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

    public VideoLoaderManager(SuggestionsLoaderManager suggestionsLoader) {
        mSuggestionsLoader = suggestionsLoader;
        mPlaylist = Playlist.instance();
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onInitDone() {
        mPlayerData = PlayerData.instance(getActivity());
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
                loadVideo(item); // play immediately
            }
        } else {
            mLastVideo = item; // save for later
        }
    }

    @Override
    public void onEngineInitialized() {
        loadVideo(mLastVideo);
        getController().setRepeatButtonState(mPlayerData.getPlaybackMode());
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
    }

    @Override
    public void onVideoLoaded(Video item) {
        mLastError = -1;
    }

    @Override
    public void onBuffering() {
        // Fix long buffering
        //Utils.postDelayed(mHandler, mPendingRestartEngine, BUFFERING_CHECK_MS);
    }

    @Override
    public void onPlay() {
        //MessageHelpers.showMessage(getActivity(), "Start playing!");

        // Seems fine. Buffering is gone.
        Utils.removeCallbacks(mHandler, mPendingRestartEngine);
    }

    @Override
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
        // Fix simultaneous videos loading (e.g. when playback ends and user opens new video)
        if (isActionsRunning()) {
            return;
        }

        int playbackMode = checkSleepTimer(mPlayerData.getPlaybackMode());

        switch (playbackMode) {
            case PlaybackEngineController.PLAYBACK_MODE_PLAY_ALL:
                onNextClicked();
                getController().showOverlay(true);
                break;
            case PlaybackEngineController.PLAYBACK_MODE_REPEAT_ONE:
                getController().setPositionMs(0);
                getController().setPlay(true);
                Utils.showRepeatInfo(getActivity(), playbackMode);
                break;
            case PlaybackEngineController.PLAYBACK_MODE_CLOSE:
                // Close player
                // Except when playing from queue
                if (!getController().isSuggestionsShown() && mPlaylist.getNext() == null) {
                    getController().finish();
                } else {
                    onNextClicked();
                    getController().showOverlay(true);
                }
                break;
            case PlaybackEngineController.PLAYBACK_MODE_PAUSE:
                // Stop player after each video.
                // Except when playing from queue
                if (mPlaylist.getNext() == null) {
                    getController().showSuggestions(true);
                    getController().setPlay(false);
                    getController().setPositionMs(0);
                    Utils.showRepeatInfo(getActivity(), playbackMode);
                } else {
                    onNextClicked();
                    getController().showOverlay(true);
                }
                break;
            case PlaybackEngineController.PLAYBACK_MODE_LIST:
                // stop player (if not playing playlist)
                Video video = getController().getVideo();
                if ((video != null && video.hasPlaylist()) || mPlaylist.getNext() != null) {
                    onNextClicked();
                    getController().showOverlay(true);
                } else {
                    getController().showSuggestions(true);
                    getController().setPlay(false);
                    getController().setPositionMs(0);
                    Utils.showRepeatInfo(getActivity(), playbackMode);
                }
                break;
        }

        Log.e(TAG, "Undetected repeat mode " + playbackMode);
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
        if (mPlayerData.isSonyTimerFixEnabled()) {
            if (System.currentTimeMillis() - mSleepTimerStartMs > 60 * 60 * 1_000) {
                MessageHelpers.showLongMessage(getActivity(), R.string.player_sleep_timer);
                playbackMode = PlaybackEngineController.PLAYBACK_MODE_PAUSE;
            }
        }

        return playbackMode;
    }

    private void loadVideo(Video item) {
        if (item != null) {
            mPlaylist.setCurrent(item);
            mLastVideo = item;
            getController().setVideo(item);
            loadFormatInfo(item);
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
            Utils.postDelayed(mHandler, mPendingNext, 1_000);
        } else if (current.isRemote) {
            openFirstVideoFromRecommended(current);
        }
    }

    private void openFirstVideoFromRecommended(Video video) {
        VideoGroup suggestions = getController().getSuggestionsByIndex(video.isRemote ? 1 : 0);
        if (suggestions != null && suggestions.getVideos() != null && suggestions.getVideos().size() > 0) {
            openVideoInt(suggestions.getVideos().get(0));
        }
    }

    private void loadFormatInfo(Video video) {
        disposeActions();

        MediaService service = YouTubeMediaService.instance();
        MediaItemManager mediaItemManager = service.getMediaItemManager();
        mFormatInfoAction = mediaItemManager.getFormatInfoObserve(video.videoId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::processFormatInfo,
                           error -> {
                               Log.e(TAG, "loadFormatInfo error: %s", error.getMessage());
                               scheduleReloadVideoTimer(1_000);
                           });
    }

    private void processFormatInfo(MediaItemFormatInfo formatInfo) {
        boolean isLive = formatInfo.isLive() || formatInfo.isLiveContent();
        String bgImageUrl = null;

        if (formatInfo.isUnplayable()) {
            getController().showError(formatInfo.getPlayabilityStatus());
            bgImageUrl = mLastVideo.getBackgroundUrl();
        } else if (formatInfo.containsDashUrl() && isLive && !PlayerTweaksData.instance(getActivity()).isLiveStreamFixEnabled()) {
            Log.d(TAG, "Found live video (current or past live stream) in dash format. Loading...");
            getController().openDashUrl(formatInfo.getDashManifestUrl());
        } else if (formatInfo.containsHlsUrl() && isLive) {
            Log.d(TAG, "Found live video (current or past live stream) in hls format. Loading...");
            getController().openHlsUrl(formatInfo.getHlsManifestUrl());
        } else if (formatInfo.containsDashVideoInfo() && !mPlayerData.isLegacyCodecsForced()) {
            Log.d(TAG, "Found regular video in dash format. Loading...");

            mMpdStreamAction = formatInfo.createMpdStreamObservable()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            dashManifest -> getController().openDash(dashManifest),
                            error -> Log.e(TAG, "createMpdStream error: %s", error.getMessage())
                    );
        } else if (formatInfo.containsUrlListInfo()) {
            Log.d(TAG, "Found url list video. This is always LQ. Loading...");
            getController().openUrlList(applyFix(formatInfo.createUrlList()));
        } else {
            Log.d(TAG, "Empty format info received. Seems future live translation. No video data to pass to the player.");
            scheduleReloadVideoTimer(30 * 1_000);
            mSuggestionsLoader.loadSuggestions(mLastVideo);
            bgImageUrl = mLastVideo.getBackgroundUrl();
        }

        Video video = getController().getVideo();
        if (video != null) {
            video.sync(formatInfo);
        }

        getController().setBackground(bgImageUrl);

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
        RxUtils.disposeActions(mFormatInfoAction, mMpdStreamAction);
        Utils.removeCallbacks(mHandler, mReloadVideoHandler, mPendingRestartEngine, mPendingNext);
    }

    private void initErrorActions() {
        // Some ciphered data could be outdated.
        // Might happen when the app wasn't used quite a long time.
        mErrorActions.put(PlayerEventListener.ERROR_TYPE_SOURCE, () -> {
            // This buffering setting could also cause such errors.
            PlayerTweaksData tweaksData = PlayerTweaksData.instance(getActivity());
            if (tweaksData.isBufferingFixEnabled()) {
                tweaksData.enableBufferingFix(false);
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
}
