package com.liskovsoft.smartyoutubetv2.common.app.models.playback.listeners;

import android.annotation.SuppressLint;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.schedulers.Schedulers;

public class HistoryUpdater extends PlayerEventListenerHelper {
    private static final String TAG = HistoryUpdater.class.getSimpleName();

    @Override
    @SuppressLint("CheckResult")
    public void onVideoLoaded(Video item) {
        MediaService service = YouTubeMediaService.instance();
        MediaItemManager mediaItemManager = service.getMediaItemManager();

        mediaItemManager.updateHistoryPositionObserve(item.mediaItem, mController.getPositionMs() / 1_000)
                .subscribeOn(Schedulers.newThread())
                .subscribe((Void v) -> {}, error -> Log.e(TAG, "History update error: " + error));
    }
}
