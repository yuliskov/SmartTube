package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.ContentBlockData;
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
    private ContentBlockData mContentBlockData;
    private List<SponsorSegment> mSponsorSegments;
    private Disposable mProgressAction;
    private Disposable mSegmentsAction;
    private boolean mIsSameSegment;

    @Override
    public void onInitDone() {
        MediaService mediaService = YouTubeMediaService.instance();
        mMediaItemManager = mediaService.getMediaItemManager();
        mContentBlockData = ContentBlockData.instance(getActivity());
    }

    @Override
    public void onVideoLoaded(Video item) {
        disposeActions();

        if (mContentBlockData.isSponsorBlockEnabled() && checkVideo(item)) {
            updateSponsorSegmentsAndWatch(item);
        }
    }

    @Override
    public void onEngineReleased() {
        disposeActions();
    }

    private boolean checkVideo(Video video) {
        return video != null && !video.isLive && !video.isUpcoming;
    }

    private void updateSponsorSegmentsAndWatch(Video item) {
        if (item == null || item.videoId == null) {
            mSponsorSegments = null;
            return;
        }

        mSegmentsAction = mMediaItemManager.getSponsorSegmentsObserve(item.videoId, mContentBlockData.getCategories())
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        segments -> {
                            mSponsorSegments = segments;
                            startPlaybackWatcher();
                        },
                        error -> Log.d(TAG, "It's ok. Nothing to block in this video. Error msg: %s", error.getMessage())
                );
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
                        error -> Log.e(TAG, "startPlaybackWatcher error: %s", error.getMessage())
                );
    }

    private void disposeActions() {
        RxUtils.disposeActions(mProgressAction, mSegmentsAction);
    }

    private void skipSegment(long positionMs) {
        if (mSponsorSegments == null) {
            return;
        }

        boolean isSegmentFound = false;

        for (SponsorSegment segment : mSponsorSegments) {
            if (positionMs >= segment.getStartMs() && positionMs < segment.getEndMs()) {
                switch (mContentBlockData.getNotificationType()) {
                    case ContentBlockData.NOTIFICATION_TYPE_NONE:
                        getController().setPositionMs(segment.getEndMs());
                        break;
                    case ContentBlockData.NOTIFICATION_TYPE_TOAST:
                        MessageHelpers.showMessage(getActivity(), getActivity().getString(R.string.msg_skipping_segment, segment.getCategory()));
                        getController().setPositionMs(segment.getEndMs());
                        break;
                    case ContentBlockData.NOTIFICATION_TYPE_DIALOG:
                        confirmSkip(segment.getEndMs(), segment.getCategory());
                        break;
                }

                isSegmentFound = true;
                break;
            }
        }

        mIsSameSegment = isSegmentFound;
    }

    private void confirmSkip(long skipPositionMs, String category) {
        if (mIsSameSegment) {
            return;
        }

        AppSettingsPresenter settingsPresenter = AppSettingsPresenter.instance(getActivity());
        settingsPresenter.clear();

        OptionItem sponsorBlockOption = UiOptionItem.from(
                getActivity().getString(R.string.confirm_segment_skip, category),
                option -> {
                    settingsPresenter.closeDialog();
                    getController().setPositionMs(skipPositionMs);
                }
        );

        settingsPresenter.appendSingleButton(sponsorBlockOption);

        settingsPresenter.showDialog(ContentBlockData.SPONSOR_BLOCK_NAME);
    }
}
