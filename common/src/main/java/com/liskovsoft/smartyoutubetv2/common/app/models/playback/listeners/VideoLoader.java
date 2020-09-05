package com.liskovsoft.smartyoutubetv2.common.app.models.playback.listeners;

import android.annotation.SuppressLint;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

import java.io.InputStream;

public class VideoLoader extends PlayerEventListenerHelper {
    private static final String TAG = VideoLoader.class.getSimpleName();
    private final Playlist mPlaylist;
    private Video mLastVideo;

    public VideoLoader() {
        mPlaylist = Playlist.instance();
    }

    @Override
    public void setFirstVideo(Video item) {
        mLastVideo = item;
    }

    @Override
    public void onEngineInitialized() {
        loadVideo(mLastVideo);
    }

    @Override
    public void onPreviousClicked() {
        loadVideo(mPlaylist.previous());
    }

    @Override
    public void onNextClicked() {
        loadVideo(mPlaylist.next());
    }

    @Override
    public void onSuggestionItemClicked(Video item) {
        loadVideo(item);
    }

    private void loadVideo(Video item) {
        if (item != null) {
            mLastVideo = item;
            mController.openVideo(item);
            loadFormatInfo(item);
        }
    }

    @SuppressLint("CheckResult")
    private void loadFormatInfo(Video video) {
        MediaService service = YouTubeMediaService.instance();
        MediaItemManager mediaItemManager = service.getMediaItemManager();
        mediaItemManager.getFormatInfoObserve(video.videoId)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(formatInfo -> {
                    if (formatInfo == null) {
                        Log.e(TAG, "loadFormatInfo: Can't obtains format info: " + video.title);
                        return;
                    }

                    InputStream dashStream = formatInfo.getMpdStream();
                    String hlsManifestUrl = formatInfo.getHlsManifestUrl();

                    if (hlsManifestUrl != null) {
                        mController.openHls(hlsManifestUrl);
                    } else {
                        mController.openDash(dashStream);
                    }
                }, error -> Log.e(TAG, "loadFormatInfo: " + error));
    }
}
