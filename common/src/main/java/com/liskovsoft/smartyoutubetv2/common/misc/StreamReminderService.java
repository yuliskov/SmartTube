package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.TickleManager.TickleListener;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.List;

public class StreamReminderService implements TickleListener {
    private static final String TAG = StreamReminderService.class.getSimpleName();
    private static StreamReminderService sInstance;
    private final MediaItemManager mItemManager;
    private final Context mContext;
    private final GeneralData mGeneralData;
    private Disposable mReminderAction;

    private StreamReminderService(Context context) {
        MediaService service = YouTubeMediaService.instance();
        mItemManager = service.getMediaItemManager();
        mContext = context.getApplicationContext();
        mGeneralData = GeneralData.instance(context);
    }

    public static StreamReminderService instance(Context context) {
        if (sInstance == null) {
            sInstance = new StreamReminderService(context);
        }

        return sInstance;
    }

    public boolean isReminderSet(Video video) {
        return mGeneralData.containsPendingStream(video);
    }

    public void toggleReminder(Video video) {
        if (video.videoId == null || !video.isUpcoming) {
            return;
        }

        if (mGeneralData.containsPendingStream(video)) {
            mGeneralData.removePendingStream(video);
        } else {
            mGeneralData.addPendingStream(video);
        }

        start();
    }

    public void start() {
        if (mGeneralData.getPendingStreams().isEmpty()) {
            TickleManager.instance().removeListener(this);
            sInstance = null;
        } else {
            TickleManager.instance().addListener(this);
        }
    }

    @Override
    public void onTickle() {
        if (mGeneralData.getPendingStreams().isEmpty()) {
            start();
            return;
        }

        RxUtils.disposeActions(mReminderAction);

        List<Observable<MediaItemFormatInfo>> observables = toObservables();

        mReminderAction = Observable.mergeDelayError(observables)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::processMetadata,
                        error -> Log.e(TAG, "loadMetadata error: %s", error.getMessage())
                );
    }

    private void processMetadata(MediaItemFormatInfo formatInfo) {
        String videoId = formatInfo.getVideoId();
        if (formatInfo.isLive() && videoId != null) {
            Utils.movePlayerToForeground(mContext);
            PlaybackPresenter.instance(mContext).openVideo(videoId);
            Video video = new Video();
            video.videoId = videoId;
            mGeneralData.removePendingStream(video);
            start();
        }
    }

    /**
     * NOTE: don't use MediaItemMetadata because it has contains isLive and isUpcoming flags
     */
    private List<Observable<MediaItemFormatInfo>> toObservables() {
        List<Observable<MediaItemFormatInfo>> result = new ArrayList<>();

        for (Video item : mGeneralData.getPendingStreams()) {
            result.add(mItemManager.getFormatInfoObserve(item.videoId));
        }

        return result;
    }
}
