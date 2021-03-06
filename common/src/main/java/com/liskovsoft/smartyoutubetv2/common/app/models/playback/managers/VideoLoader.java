package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import android.os.Handler;
import android.os.Looper;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackEngineController;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.utils.RxUtils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class VideoLoader extends PlayerEventListenerHelper {
    private static final String TAG = VideoLoader.class.getSimpleName();
    private static final boolean ENABLE_4K_FIX = false;
    private final Playlist mPlaylist;
    private final Handler mHandler;
    private final SuggestionsLoader mSuggestionsLoader;
    private Video mLastVideo;
    private Disposable mFormatInfoAction;
    private Disposable mMpdStreamAction;
    private final Runnable mReloadVideoHandler = () -> loadVideo(mLastVideo);
    private long mPrevErrorTimeMs;
    private PlayerData mPlayerData;
    private long mSleepTimerStartMs;
    private final Runnable mPendingNext = () -> {
        if (getController() != null) {
            openVideoFromNext(getController().getVideo(), false);
        }
    };

    public VideoLoader(SuggestionsLoader suggestionsLoader) {
        mSuggestionsLoader = suggestionsLoader;
        mPlaylist = Playlist.instance();
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onInitDone() {
        mPlayerData = PlayerData.instance(getActivity());
    }

    @Override
    public void openVideo(Video item) {
        mPlaylist.add(item);

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
    public void onEngineError(int type) {
        Log.e(TAG, "Player error occurred: %s. Trying to fix…", type);

        // Some ciphered data might be stalled.
        // Might happen when the app wasn't used quite a long time.
        MessageHelpers.showMessage(getActivity(), R.string.msg_player_error, type);
        YouTubeMediaService.instance().invalidateCache();
        getController().restartEngine(); // properly save position of the current track

        //if (type == PlayerEventListener.ERROR_TYPE_SOURCE ||
        //    type == PlayerEventListener.ERROR_TYPE_RENDERER ||
        //    type == PlayerEventListener.ERROR_TYPE_REMOTE) {
        //    // Some ciphered data might be stalled.
        //    // Might happen when the app wasn't used quite a long time.
        //    YouTubeMediaService.instance().invalidateCache();
        //    loadVideo(mLastVideo);
        //} else {
        //    MessageHelpers.showMessage(getActivity(), R.string.msg_player_error);
        //}

        //getController().showControls(true);
    }

    @Override
    public boolean onPreviousClicked() {
        openVideoInt(mPlaylist.previous());

        return true;
    }

    @Override
    public boolean onNextClicked() {
        Video next = mPlaylist.next();

        if (next == null) {
            openVideoFromNext(getController().getVideo(), true);
        } else {
            openVideoInt(next);
        }

        return true;
    }

    @Override
    public void onPlayEnd() {
        int playbackMode = checkSleepTimer(mPlayerData.getPlaybackMode());

        switch (playbackMode) {
            case PlaybackEngineController.PLAYBACK_MODE_PLAY_ALL:
                onNextClicked();
                getController().showControls(true);
                break;
            case PlaybackEngineController.PLAYBACK_MODE_REPEAT_ONE:
                //loadVideo(mLastVideo);
                getController().setPositionMs(0);
                getController().setPlay(true);
                break;
            case PlaybackEngineController.PLAYBACK_MODE_CLOSE:
                // close player
                if (!getController().isSuggestionsShown()) {
                    getController().exit();
                }
                break;
            case PlaybackEngineController.PLAYBACK_MODE_PAUSE:
                // stop player after each video
                getController().showSuggestions(true);
                getController().setPlay(false);
                break;
            case PlaybackEngineController.PLAYBACK_MODE_LIST:
                // stop player (if not playing playlist)
                Video video = getController().getVideo();
                if (video != null && video.playlistId != null) {
                    onNextClicked();
                    getController().showControls(true);
                } else {
                    getController().showSuggestions(true);
                    getController().setPlay(false);
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
    public void onRepeatModeClicked(int modeIndex) {
        mPlayerData.setPlaybackMode(modeIndex);
        showBriefInfo(modeIndex);
    }

    @Override
    public boolean onKeyDown(int keyCode) {
        mSleepTimerStartMs = System.currentTimeMillis();

        return false;
    }

    private int checkSleepTimer(int playbackMode) {
        if (mPlayerData.isSleepTimerEnabled()) {
            if (System.currentTimeMillis() - mSleepTimerStartMs > 60 * 60 * 1_000) {
                MessageHelpers.showLongMessage(getActivity(), R.string.player_sleep_timer);
                playbackMode = PlaybackEngineController.PLAYBACK_MODE_PAUSE;
            }
        }

        return playbackMode;
    }

    private void showBriefInfo(int modeIndex) {
        switch (modeIndex) {
            case PlaybackEngineController.PLAYBACK_MODE_PLAY_ALL:
                MessageHelpers.showMessage(getActivity(), R.string.repeat_mode_all);
                break;
            case PlaybackEngineController.PLAYBACK_MODE_REPEAT_ONE:
                MessageHelpers.showMessage(getActivity(), R.string.repeat_mode_one);
                break;
            case PlaybackEngineController.PLAYBACK_MODE_PAUSE:
                MessageHelpers.showMessage(getActivity(), R.string.repeat_mode_pause);
                break;
            case PlaybackEngineController.PLAYBACK_MODE_LIST:
                MessageHelpers.showMessage(getActivity(), R.string.repeat_mode_pause_alt);
                break;
            case PlaybackEngineController.PLAYBACK_MODE_CLOSE:
                MessageHelpers.showMessage(getActivity(), R.string.repeat_mode_none);
                break;
        }
    }

    private void disposeActions() {
        RxUtils.disposeActions(mFormatInfoAction, mMpdStreamAction);
        mHandler.removeCallbacks(mReloadVideoHandler);
    }

    private void loadVideo(Video item) {
        if (item != null) {
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
        } else {
            if (showLoadingMsg) {
                MessageHelpers.showMessageThrottled(getActivity(), R.string.wait_data_loading);
            }
            startPendingNext();
        }
    }

    private void loadFormatInfo(Video video) {
        disposeActions();

        MediaService service = YouTubeMediaService.instance();
        MediaItemManager mediaItemManager = service.getMediaItemManager();
        mFormatInfoAction = mediaItemManager.getFormatInfoObserve(video.videoId)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::processFormatInfo,
                           error -> {
                               Log.e(TAG, "loadFormatInfo error: %s", error.getMessage());
                               scheduleReloadVideoTimer(1_000);
                           });
    }

    private void processFormatInfo(MediaItemFormatInfo formatInfo) {
        if (formatInfo.isUnplayable()) {
            getController().showError(formatInfo.getPlayabilityStatus());
        } else if (ENABLE_4K_FIX && formatInfo.containsDashUrl() && formatInfo.isLive() && formatInfo.isStreamSeekable()) {
            Log.d(TAG, "Found live video in dash format. Loading...");
            getController().openDashUrl(formatInfo.getDashManifestUrl());
        } else if (formatInfo.containsHlsUrl()) {
            Log.d(TAG, "Found live video (current and past) in hls format. Loading...");
            getController().openHlsUrl(formatInfo.getHlsManifestUrl());
        } else if (formatInfo.containsDashVideoInfo() && !mPlayerData.isLowQualityEnabled()) {
            Log.d(TAG, "Found regular video in dash format. Loading...");

            mMpdStreamAction = formatInfo.createMpdStreamObservable()
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            getController()::openDash,
                            error -> Log.e(TAG, "createMpdStream error: %s", error.getMessage())
                    );
        } else if (formatInfo.containsUrlListInfo()) {
            Log.d(TAG, "Found url list video. This is always LQ. Loading...");
            getController().openUrlList(formatInfo.createUrlList());
        } else {
            Log.d(TAG, "Empty format info received. Seems future live translation. No video data to pass to the player.");
            scheduleReloadVideoTimer(30 * 1_000);
            mSuggestionsLoader.loadSuggestions(mLastVideo);
        }
    }

    private void scheduleReloadVideoTimer(int reloadIntervalMs) {
        if (getController().isEngineInitialized()) {
            Log.d(TAG, "Starting check for the future stream...");
            getController().showControls(true);
            mHandler.postDelayed(mReloadVideoHandler, reloadIntervalMs);
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
        stopPendingNext();

        if (item == null) {
            return;
        }

        if (item.isVideo()) {
            getController().showControls(true);
            PlaybackPresenter.instance(getActivity()).openVideo(item, false);
        } else if (item.isChannel()) {
            ChannelPresenter.instance(getActivity()).openChannel(item);
        } else {
            Log.e(TAG, "Video item doesn't contain needed data!");
        }
    }

    private void stopPendingNext() {
        mHandler.removeCallbacks(mPendingNext);
    }

    private void startPendingNext() {
        mHandler.removeCallbacks(mPendingNext);
        mHandler.postDelayed(mPendingNext, 1_000);
    }
}
