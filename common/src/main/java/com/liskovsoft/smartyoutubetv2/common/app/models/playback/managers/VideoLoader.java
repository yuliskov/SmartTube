package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import android.app.Activity;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackUiController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.SuggestionsLoader.MetadataListener;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class VideoLoader extends PlayerEventListenerHelper implements MetadataListener {
    private static final String TAG = VideoLoader.class.getSimpleName();
    private final Playlist mPlaylist;
    private Video mLastVideo;
    private Video mErrorVideo;
    private Disposable mMetadataAction;
    private Disposable mFormatInfoAction;
    private boolean mEngineInitialized;
    private MediaItem mCachedNextVideo;
    private int mRepeatMode = PlaybackUiController.REPEAT_ALL;

    public VideoLoader() {
        mPlaylist = Playlist.instance();
    }

    @Override
    public void onInitDone() {
        mRepeatMode = AppPrefs.instance(mActivity).getVideoLoaderData(PlaybackUiController.REPEAT_ALL);
    }

    @Override
    public void openVideo(Video item) {
        mPlaylist.add(item);

        if (mEngineInitialized) { // player is initialized
            if (!item.equals(mLastVideo)) {
                loadVideo(item); // play immediately
            }
        } else {
            mLastVideo = item; // save for later
        }
    }

    @Override
    public void onEngineInitialized() {
        mEngineInitialized = true;
        loadVideo(mLastVideo);
        mController.setRepeatButtonState(mRepeatMode);
    }

    @Override
    public void onEngineReleased() {
        mEngineInitialized = false;
        disposeActions();
    }

    @Override
    public void onTrackSelected(FormatItem track) {
        if (mController.hasNoMedia()) {
            Log.e(TAG, "Engine lost his track. Is user selected unsupported format? Restarting...");
            mController.restartEngine();
        }
    }

    @Override
    public void onEngineError(int type) {
        // restart once per video
        if (mErrorVideo != mLastVideo) {
            Log.e(TAG, "Player error occurred. Restarting engine once...");
            mController.restartEngine();
        }

        mErrorVideo = mLastVideo;
    }

    @Override
    public boolean onPreviousClicked() {
        disposeActions();

        loadVideo(mPlaylist.previous());

        return true;
    }

    @Override
    public boolean onNextClicked() {
        disposeActions();

        Video next = mPlaylist.next();

        if (next == null) {
            loadVideoFromMetadata(mController.getVideo());
        } else {
            loadVideo(next);
        }

        return true;
    }

    @Override
    public void onPlayEnd() {
        switch (mRepeatMode) {
            case PlaybackUiController.REPEAT_ALL:
                onNextClicked();
                break;
            case PlaybackUiController.REPEAT_ONE:
                loadVideo(mLastVideo);
                break;
            case PlaybackUiController.REPEAT_NONE:
                // close player
                mController.exit();
                break;
        }

        Log.e(TAG, "Undetected repeat mode " + mRepeatMode);
    }

    @Override
    public void onSuggestionItemClicked(Video item) {
        if (item.isVideo()) {
            mPlaylist.add(item);
            loadVideo(item);
        } else if (item.isChannel()) {
            ChannelPresenter.instance(mActivity).openChannel(item);
        } else {
            Log.e(TAG, "Video item doesn't contain needed data!");
        }
    }

    @Override
    public void onMetadata(MediaItemMetadata metadata) {
        mCachedNextVideo = metadata.getNextVideo();
    }

    @Override
    public void onRepeatModeClicked(int modeIndex) {
        mRepeatMode = modeIndex;
        AppPrefs.instance(mActivity).setVideoLoaderData(mRepeatMode);
    }

    private void disposeActions() {
        if (mMetadataAction != null && !mMetadataAction.isDisposed()) {
            mMetadataAction.dispose();
        }

        if (mFormatInfoAction != null && !mFormatInfoAction.isDisposed()) {
            mFormatInfoAction.dispose();
        }
    }

    private void loadVideo(Video item) {
        if (item != null) {
            mLastVideo = item;
            mController.setVideo(item);
            loadFormatInfo(item);
        }
    }

    private void loadVideoFromNext(MediaItem nextVideo) {
        Video item = Video.from(nextVideo);
        mPlaylist.add(item);
        loadVideo(item);
    }

    private void loadVideoFromMetadata(MediaItemMetadata metadata) {
        MediaItem nextVideo = metadata.getNextVideo();
        loadVideoFromNext(nextVideo);
    }

    private void loadVideoFromMetadata(Video current) {
        if (current == null) {
            return;
        }

        // Significantly improves next video loading time!
        if (mCachedNextVideo != null) {
            loadVideoFromNext(mCachedNextVideo);
            return;
        }

        MediaService service = YouTubeMediaService.instance();
        MediaItemManager mediaItemManager = service.getMediaItemManager();
        mMetadataAction = mediaItemManager.getMetadataObserve(current.mediaItem)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::loadVideoFromMetadata, error -> Log.e(TAG, "loadNextVideo error: " + error));
    }

    private void loadFormatInfo(Video video) {
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
            Log.d(TAG, "Found hls video. Loading...");
            mController.openHls(formatInfo.getHlsManifestUrl());
        } else if (formatInfo.containsDashInfo()) {
            Log.d(TAG, "Found dash video. Loading...");

            Disposable action = formatInfo.createMpdStreamObservable()
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(mController::openDash, error -> Log.e(TAG, "createMpdStream error: " + error));
        } else if (formatInfo.containsUrlListInfo()) {
            Log.d(TAG, "Found url list video. Loading...");
            mController.openUrlList(formatInfo.createUrlList());
        } else {
            Log.e(TAG, "Empty format info received. No video data to pass to the player.");
            mController.showControls(true);
        }
    }
}
