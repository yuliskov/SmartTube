package com.liskovsoft.smartyoutubetv2.common.app.models.playback.processors;

import android.annotation.SuppressLint;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

import java.io.InputStream;

public class VideoLoader extends PlayerEventListenerHelper {
    private static final String TAG = VideoLoader.class.getSimpleName();
    private final Playlist mPlaylist;
    private final PlayerController mController;

    public VideoLoader(PlayerController controller) {
        mController = controller;
        mPlaylist = Playlist.instance();
    }

    @Override
    public void onInit(Video item) {
        loadItem(item);
    }

    @Override
    public void onPrevious() {
        loadItem(mPlaylist.previous());
    }

    @Override
    public void onNext() {
        loadItem(mPlaylist.next());
    }

    @Override
    public void onSuggestionItemClicked(Video item) {
        loadItem(item);
    }

    private void loadItem(Video item) {
        if (item != null) {
            mController.initTitle(item);
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

                    if (dashStream != null) {
                        mController.openDash(dashStream);
                    } else {
                        mController.openHls(hlsManifestUrl);
                    }
                }, error -> Log.e(TAG, "loadFormatInfo: " + error));
    }
}
