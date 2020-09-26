package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class VideoLoader extends PlayerEventListenerHelper {
    private static final String TAG = VideoLoader.class.getSimpleName();
    private final Playlist mPlaylist;
    private Video mLastVideo;
    private Video mErrorVideo;
    private Disposable mMetadataAction;
    private Disposable mFormatInfoAction;
    private boolean mEngineInitialized;

    public VideoLoader() {
        mPlaylist = Playlist.instance();
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
    }

    @Override
    public void onEngineReleased() {
        mEngineInitialized = false;
        disposeActions();
    }

    @Override
    public void onEngineError(int type) {
        // restart once per video
        if (mErrorVideo != mLastVideo) {
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

    private void disposeActions() {
        if (mMetadataAction != null && !mMetadataAction.isDisposed()) {
            mMetadataAction.dispose();
        }

        if (mFormatInfoAction != null && !mFormatInfoAction.isDisposed()) {
            mFormatInfoAction.dispose();
        }
    }

    @Override
    public void onPlayEnd() {
        onNextClicked();
    }

    @Override
    public void onSuggestionItemClicked(Video item) {
        mPlaylist.add(item);
        loadVideo(item);
    }

    private void loadVideo(Video item) {
        if (item != null) {
            mLastVideo = item;
            mController.setVideo(item);
            loadFormatInfo(item);
        }
    }

    private void loadVideoFromMetadata(MediaItemMetadata metadata) {
        MediaItem nextVideo = metadata.getNextVideo();
        Video item = Video.from(nextVideo);
        mPlaylist.add(item);
        loadVideo(item);
    }

    private void loadVideoFromMetadata(Video current) {
        if (current == null) {
            return;
        }

        if (current.cachedMetadata != null) {
            loadVideoFromMetadata(current.cachedMetadata);
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
                .subscribe(this::loadFormatInfo,
                           error -> Log.e(TAG, "loadFormatInfo error: " + error));
    }

    private void loadFormatInfo(MediaItemFormatInfo formatInfo) {
        if (formatInfo.containsHlsInfo()) {
            Log.d(TAG, "Found hls video. Loading...");
            mController.openHls(formatInfo.getHlsManifestUrl());
        } else if (formatInfo.containsDashInfo()) {
            Log.d(TAG, "Found dash video. Loading...");
            mController.openDash(formatInfo.createMpdStream());
        } else if (formatInfo.containsUrlListInfo()) {
            Log.d(TAG, "Found simple url video. Loading...");
            mController.openUrlList(formatInfo.createUrlList());
        } else {
            Log.e(TAG, "Empty format info received. No video data to pass to the player.");
        }
    }
}
