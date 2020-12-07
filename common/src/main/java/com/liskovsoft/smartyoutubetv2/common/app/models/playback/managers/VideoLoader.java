package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackUiController;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.utils.RxUtils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class VideoLoader extends PlayerEventListenerHelper {
    private static final String TAG = VideoLoader.class.getSimpleName();
    private final Playlist mPlaylist;
    private final Handler mHandler;
    private Video mLastVideo;
    private Video mErrorVideo;
    private Disposable mFormatInfoAction;
    private Disposable mMpdStreamAction;
    private int mRepeatMode = PlaybackUiController.REPEAT_ALL;
    private final Runnable mReloadVideoHandler = () -> loadVideo(mLastVideo);

    public VideoLoader() {
        mPlaylist = Playlist.instance();
        mHandler = new Handler(Looper.myLooper());
    }

    @Override
    public void onInitDone() {
        mRepeatMode = AppPrefs.instance(getActivity()).getVideoLoaderData(PlaybackUiController.REPEAT_ALL);
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
        getController().setRepeatButtonState(mRepeatMode);
    }

    @Override
    public void onEngineReleased() {
        disposeActions();
    }

    @Override
    public void onEngineError(int type) {
        // restart once per video
        if (mErrorVideo != mLastVideo) {
            Log.e(TAG, "Player error occurred. Restarting engine once...");
            mErrorVideo = mLastVideo;
            YouTubeMediaService.instance().invalidateCache(); // some data might be stalled
            getController().reloadPlayback(); // re-download video data
        } else {
            getController().showControls(true);
        }
    }

    @Override
    public boolean onPreviousClicked() {
        loadVideo(mPlaylist.previous());

        return true;
    }

    @Override
    public boolean onNextClicked() {
        Video next = mPlaylist.next();

        if (next == null) {
            loadVideoFromMetadata(getController().getVideo());
        } else {
            loadVideo(next);
        }

        return true;
    }

    @Override
    public void onPlayEnd() {
        // Suggestions is opened. Seems that user want to stay here.
        if (!getController().isSuggestionsShown()) {
            switch (mRepeatMode) {
                case PlaybackUiController.REPEAT_ALL:
                    onNextClicked();
                    if (!getController().isInPIPMode()) {
                        getController().showControls(true);
                    }
                    break;
                case PlaybackUiController.REPEAT_ONE:
                    loadVideo(mLastVideo);
                    break;
                case PlaybackUiController.REPEAT_NONE:
                    // close player
                    getController().exit();
                    break;
                case PlaybackUiController.REPEAT_PAUSE:
                    // pause player
                    getController().showControls(true);
                    break;
            }

            Log.e(TAG, "Undetected repeat mode " + mRepeatMode);
        }
    }

    @Override
    public void onSuggestionItemClicked(Video item) {
        if (item.isVideo()) {
            mPlaylist.add(item);
            loadVideo(item);
        } else if (item.isChannel()) {
            ChannelPresenter.instance(getActivity()).openChannel(item);
        } else {
            Log.e(TAG, "Video item doesn't contain needed data!");
        }
    }

    @Override
    public void onRepeatModeClicked(int modeIndex) {
        mRepeatMode = modeIndex;
        AppPrefs.instance(getActivity()).setVideoLoaderData(mRepeatMode);
        showBriefInfo(modeIndex);
    }

    private void showBriefInfo(int modeIndex) {
        switch (modeIndex) {
            case PlaybackUiController.REPEAT_ALL:
                MessageHelpers.showMessage(getActivity(), R.string.repeat_mode_all);
                break;
            case PlaybackUiController.REPEAT_ONE:
                MessageHelpers.showMessage(getActivity(), R.string.repeat_mode_one);
                break;
            case PlaybackUiController.REPEAT_PAUSE:
                MessageHelpers.showMessage(getActivity(), R.string.repeat_mode_pause);
                break;
            case PlaybackUiController.REPEAT_NONE:
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

    private void loadVideoFromNext(MediaItem nextVideo) {
        Video item = Video.from(nextVideo);
        mPlaylist.add(item);
        loadVideo(item);
    }

    private void loadVideoFromMetadata(Video current) {
        if (current == null) {
            return;
        }

        // Significantly improves next video loading time!
        if (current.nextMediaItem != null) {
            loadVideoFromNext(current.nextMediaItem);
        } else {
            MessageHelpers.showMessageThrottled(getActivity(), R.string.next_video_info_is_not_loaded_yet);
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
                           error -> Log.e(TAG, "loadFormatInfo error: " + error));
    }

    private void processFormatInfo(MediaItemFormatInfo formatInfo) {
        if (formatInfo.containsHlsInfo()) {
            Log.d(TAG, "Found hls video. Live translation. Loading...");
            getController().openHls(formatInfo.getHlsManifestUrl());
        } else if (formatInfo.containsDashInfo()) {
            Log.d(TAG, "Found dash video. Main video format. Loading...");

            mMpdStreamAction = formatInfo.createMpdStreamObservable()
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(getController()::openDash, error -> Log.e(TAG, "createMpdStream error: " + error));
        } else if (formatInfo.containsUrlListInfo()) {
            Log.d(TAG, "Found url list video. This is always LQ. Loading...");
            getController().openUrlList(formatInfo.createUrlList());
        } else {
            Log.d(TAG, "Empty format info received. Seems future translation. No video data to pass to the player.");
            scheduleReloadVideoTimer();
        }
    }

    private void scheduleReloadVideoTimer() {
        Log.d(TAG, "Starting check for the future stream...");
        getController().showControls(true);
        mHandler.postDelayed(mReloadVideoHandler, 30 * 1_000);
    }
}
