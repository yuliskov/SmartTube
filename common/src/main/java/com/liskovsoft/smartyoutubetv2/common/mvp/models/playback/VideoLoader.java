package com.liskovsoft.smartyoutubetv2.common.mvp.models.playback;

import android.annotation.SuppressLint;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.data.Video;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

import java.io.InputStream;

public class VideoLoader extends PlayerCommandProcessorHelper {
    private static final String TAG = VideoLoader.class.getSimpleName();
    private PlayerCommandHandler mCommandHandler;

    @Override
    public void setCommandHandler(PlayerCommandHandler commandHandler) {
        mCommandHandler = commandHandler;
    }

    @Override
    public void onOpenVideo(Video item) {
        mCommandHandler.initTitle(item);
        loadFormatInfo(item);
    }

    @Override
    public void onPrevious() {
        //
    }

    @Override
    public void onNext() {
        // 
    }

    @Override
    public void onSuggestionItemClicked(Video item) {
        mCommandHandler.initTitle(item);
        loadFormatInfo(item);
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
                        mCommandHandler.openDash(dashStream);
                    } else {
                        mCommandHandler.openHls(hlsManifestUrl);
                    }
                }, error -> Log.e(TAG, "loadFormatInfo: " + error));
    }
}
