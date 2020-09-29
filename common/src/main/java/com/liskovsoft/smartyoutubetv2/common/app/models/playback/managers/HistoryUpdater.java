package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class HistoryUpdater extends PlayerEventListenerHelper {
    private static final String TAG = HistoryUpdater.class.getSimpleName();
    private Disposable mHistoryAction;

    @Override
    public void onSourceChanged(Video item) {
        updateHistory(item);
    }

    private void updateHistory(Video item) {
        MediaService service = YouTubeMediaService.instance();
        MediaItemManager mediaItemManager = service.getMediaItemManager();

        Observable<Void> historyObservable;

        long positionSec = mController.getPositionMs() / 1_000;

        if (item.mediaItem != null) {
            historyObservable = mediaItemManager.updateHistoryPositionObserve(item.mediaItem, positionSec);
        } else { // video launched form ATV channels
            historyObservable = mediaItemManager.updateHistoryPositionObserve(item.videoId, positionSec);
        }

        mHistoryAction = historyObservable
                .subscribeOn(Schedulers.newThread())
                .subscribe((Void v) -> {}, error -> Log.e(TAG, "History update error: " + error));
    }
}
