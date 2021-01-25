package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.utils.RxUtils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class ContentBlockManager extends PlayerEventListenerHelper {
    private static final String TAG = ContentBlockManager.class.getSimpleName();
    private MediaItemManager mMediaItemManager;
    private PlayerData mPlayerData;
    private List<SponsorSegment> mSponsorSegments;
    private Disposable mProgressAction;
    private Disposable mSegmentsAction;

    @Override
    public void onInitDone() {
        MediaService mediaService = YouTubeMediaService.instance();
        mMediaItemManager = mediaService.getMediaItemManager();
        mPlayerData = PlayerData.instance(getActivity());
    }

    @Override
    public void onVideoLoaded(Video item) {
        if (mPlayerData.isSponsorBlockEnabled()) {
            updateSponsorSegmentsAndWatch(item);
        } else {
            disposeActions();
        }
    }

    private void updateSponsorSegmentsAndWatch(Video item) {
        if (item == null || item.videoId == null) {
            mSponsorSegments = null;
            return;
        }

        mSegmentsAction = mMediaItemManager.getSponsorSegmentsObserve(item.videoId)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        segments -> {
                            mSponsorSegments = segments;
                            startPlaybackWatcher();
                        },
                        error -> Log.e(TAG, "updateSponsorSegments error: %s", error)
                );
    }

    @Override
    public void onEngineReleased() {
        disposeActions();
    }

    private void startPlaybackWatcher() {
        Observable<Long> playbackProgressObservable =
                Observable.interval(1, TimeUnit.SECONDS)
                        .map((val) -> getController().getPositionMs());

        mProgressAction = playbackProgressObservable
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::skipSegment,
                        error -> Log.e(TAG, "startPlaybackWatcher error: %s", error)
                );
    }

    private void disposeActions() {
        RxUtils.disposeActions(mProgressAction, mSegmentsAction);
    }

    private void skipSegment(long positionMs) {
        if (mSponsorSegments == null || getController() == null) {
            return;
        }

        for (SponsorSegment segment : mSponsorSegments) {
            if (positionMs >= segment.getStartMs() && positionMs <= segment.getEndMs()) {
                getController().setPositionMs(segment.getEndMs());
                break;
            }
        }
    }
}
