package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.SuggestionsLoader.MetadataListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.ContentBlockData;
import com.liskovsoft.smartyoutubetv2.common.utils.RxUtils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ContentBlockManager extends PlayerEventListenerHelper implements MetadataListener {
    private static final String TAG = ContentBlockManager.class.getSimpleName();
    private static final long SEGMENT_CHECK_DURATION_MS = 1_000;
    private MediaItemManager mMediaItemManager;
    private ContentBlockData mContentBlockData;
    private Video mVideo;
    private List<SponsorSegment> mSponsorSegments;
    private Disposable mProgressAction;
    private Disposable mSegmentsAction;
    private boolean mIsSameSegment;
    private Map<String, Integer> mLocalizedMapping;

    @Override
    public void onInitDone() {
        MediaService mediaService = YouTubeMediaService.instance();
        mMediaItemManager = mediaService.getMediaItemManager();
        mContentBlockData = ContentBlockData.instance(getActivity());
        initLocalizedMapping();
    }

    private void initLocalizedMapping() {
        mLocalizedMapping = new HashMap<>();
        mLocalizedMapping.put(SponsorSegment.CATEGORY_SPONSOR, R.string.content_block_sponsor);
        mLocalizedMapping.put(SponsorSegment.CATEGORY_INTRO, R.string.content_block_intro);
        mLocalizedMapping.put(SponsorSegment.CATEGORY_OUTRO, R.string.content_block_outro);
        mLocalizedMapping.put(SponsorSegment.CATEGORY_SELF_PROMO, R.string.content_block_self_promo);
        mLocalizedMapping.put(SponsorSegment.CATEGORY_INTERACTION, R.string.content_block_interaction);
        mLocalizedMapping.put(SponsorSegment.CATEGORY_MUSIC_OFF_TOPIC, R.string.content_block_music_off_topic);
    }

    @Override
    public void onVideoLoaded(Video item) {
        disposeActions();

        if (mContentBlockData.isSponsorBlockEnabled() && checkVideo(item)) {
            updateSponsorSegmentsAndWatch(item);
        }
    }

    @Override
    public void onMetadata(MediaItemMetadata metadata) {
        // Disable sponsor for the live streams.
        // Fix when using remote control.
        if (!mContentBlockData.isSponsorBlockEnabled() || !checkVideo(getController().getVideo())) {
            disposeActions();
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
                            mVideo = item;
                            mSponsorSegments = segments;
                            startPlaybackWatcher();
                        },
                        error -> Log.d(TAG, "It's ok. Nothing to block in this video. Error msg: %s", error.getMessage())
                );
    }

    private void startPlaybackWatcher() {
        // Warn. Try to not access player object here.
        // Or you'll get "Player is accessed on the wrong thread" error.
        Observable<Long> playbackProgressObservable =
                Observable.interval(1, TimeUnit.SECONDS);

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
        mSponsorSegments = null;
        mVideo = null;
    }

    private void skipSegment(long interval) {
        if (mSponsorSegments == null || !Video.equals(mVideo, getController().getVideo())) {
            return;
        }

        boolean isSegmentFound = false;
        SponsorSegment foundSegment = null;

        for (SponsorSegment segment : mSponsorSegments) {
            if (isPositionAtSegmentStart(getController().getPositionMs(), segment)) {
                isSegmentFound = true;
                foundSegment = segment;
                Integer resId = mLocalizedMapping.get(segment.getCategory());
                String localizedCategory = resId != null ? getActivity().getString(resId) : segment.getCategory();

                int type = mContentBlockData.getNotificationType();

                if (type == ContentBlockData.NOTIFICATION_TYPE_NONE || getController().isInPIPMode()) {
                    getController().setPositionMs(segment.getEndMs());
                } else if (type == ContentBlockData.NOTIFICATION_TYPE_TOAST) {
                    messageSkip(segment.getEndMs(), localizedCategory);
                } else if (type == ContentBlockData.NOTIFICATION_TYPE_DIALOG) {
                    confirmSkip(segment.getEndMs(), localizedCategory);
                }

                break;
            }
        }

        // Skip each segment only once
        if (foundSegment != null) {
            mSponsorSegments.remove(foundSegment);
        }

        mIsSameSegment = isSegmentFound;
    }

    private boolean isPositionAtSegmentStart(long positionMs, SponsorSegment segment) {
        // Note. Getting into account playback speed. Also check that the zone is long enough.
        float checkEndMs = segment.getStartMs() + SEGMENT_CHECK_DURATION_MS * getController().getSpeed();
        return positionMs >= segment.getStartMs() && positionMs <= checkEndMs && checkEndMs <= segment.getEndMs();
    }

    private boolean isPositionInsideSegment(long positionMs, SponsorSegment segment) {
        return positionMs >= segment.getStartMs() && positionMs < segment.getEndMs();
    }

    private void messageSkip(long skipPositionMs, String category) {
        MessageHelpers.showMessage(getActivity(), getActivity().getString(R.string.msg_skipping_segment, category));
        getController().setPositionMs(skipPositionMs);
    }

    private void confirmSkip(long skipPositionMs, String category) {
        if (mIsSameSegment) {
            return;
        }

        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getActivity());
        settingsPresenter.clear();

        OptionItem sponsorBlockOption = UiOptionItem.from(
                getActivity().getString(R.string.confirm_segment_skip, category),
                option -> {
                    settingsPresenter.closeDialog();
                    getController().setPositionMs(skipPositionMs);
                }
        );

        settingsPresenter.appendSingleButton(sponsorBlockOption);
        settingsPresenter.setCloseTimeoutMs(skipPositionMs - getController().getPositionMs());

        settingsPresenter.showDialog(ContentBlockData.SPONSOR_BLOCK_NAME);
    }
}
