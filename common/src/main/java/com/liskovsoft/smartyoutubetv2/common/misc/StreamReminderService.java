package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.util.Pair;

import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.misc.TickleManager.TickleListener;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

import java.util.ArrayList;
import java.util.List;

public class StreamReminderService implements TickleListener {
    private static final String TAG = StreamReminderService.class.getSimpleName();
    private static StreamReminderService sInstance;
    private final MediaItemService mMediaItemService;
    private final Context mContext;
    private final GeneralData mGeneralData;
    private Disposable mReminderAction;

    private StreamReminderService(Context context) {
        ServiceManager service = YouTubeServiceManager.instance();
        mMediaItemService = service.getMediaItemService();
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

        startStop();
    }

    public void startStop() {
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
            startStop();
            return;
        }

        RxHelper.disposeActions(mReminderAction);

        List<Observable<Pair<Video, MediaItemFormatInfo>>> observables = toObservables();

        mReminderAction = Observable.mergeDelayError(observables)
                .subscribe(
                        this::processMetadata,
                        error -> Log.e(TAG, "loadMetadata error: %s", error.getMessage())
                );
    }

    private void processMetadata(Pair<Video, MediaItemFormatInfo> metadata) {
        Video origin = metadata.first;
        MediaItemFormatInfo formatInfo = metadata.second;
        if (formatInfo.containsMedia() && formatInfo.getVideoId() != null) {
            Video video = new Video();
            video.title = formatInfo.getTitle();
            video.videoId = formatInfo.getVideoId();
            video.isPending = true;

            Playlist playlist = Playlist.instance();
            Video current = playlist.getCurrent();

            if (current != null && current.isPending && ViewManager.instance(mContext).isPlayerInForeground()) {
                playlist.add(video);
            } else {
                ViewManager.instance(mContext).movePlayerToForeground();
                PlaybackPresenter.instance(mContext).openVideo(video);
                MessageHelpers.showLongMessage(mContext, R.string.starting_stream);
            }

            mGeneralData.removePendingStream(video);
            startStop();
        } else if (formatInfo.isUnplayable()) {
            mGeneralData.removePendingStream(origin);
            startStop();
        }
    }

    /**
     * NOTE: don't use MediaItemMetadata because it has contains isLive and isUpcoming flags
     */
    private List<Observable<Pair<Video, MediaItemFormatInfo>>> toObservables() {
        List<Observable<Pair<Video, MediaItemFormatInfo>>> result = new ArrayList<>();

        for (Video item : mGeneralData.getPendingStreams()) {
            result.add(mMediaItemService.getFormatInfoObserve(item.videoId)
                    .map(info -> new Pair<>(item, info)));
        }

        return result;
    }
}
