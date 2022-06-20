package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import android.graphics.Color;
import androidx.core.content.ContextCompat;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.SuggestionsLoaderManager.MetadataListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.ContentBlockData;
import com.liskovsoft.sharedutils.rx.RxUtils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ContentBlockManager extends PlayerEventListenerHelper implements MetadataListener {
    private static final String TAG = ContentBlockManager.class.getSimpleName();
    private static final long SEGMENT_CHECK_LENGTH_MS = 3_000;
    private MediaItemManager mMediaItemManager;
    private ContentBlockData mContentBlockData;
    private Video mVideo;
    private List<SponsorSegment> mSponsorSegments;
    private Disposable mProgressAction;
    private Disposable mSegmentsAction;
    private boolean mIsSameSegment;

    public static class SeekBarSegment {
        public int startProgress;
        public int endProgress;
        public int color = Color.GREEN;
    }

    public static class SegmentAction {
        public String segmentCategory;
        public int actionType;

        public static SegmentAction from(String spec) {
            if (spec == null) {
                return null;
            }

            String[] split = spec.split(",");

            if (split.length != 2) {
                return null;
            }

            String name = Helpers.parseStr(split[0]);
            int action = Helpers.parseInt(split[1]);

            return from(name, action);
        }

        public static SegmentAction from(String name, int action) {
            SegmentAction blockedSegment = new SegmentAction();
            blockedSegment.segmentCategory = name;
            blockedSegment.actionType = action;

            return blockedSegment;
        }

        @Override
        public String toString() {
            return String.format("%s,%s", segmentCategory, actionType);
        }
    }

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
        if (item == null || item.videoId == null || mContentBlockData.getEnabledCategories().isEmpty()) {
            mSponsorSegments = null;
            return;
        }

        // Reset colors
        getController().setSeekBarSegments(null);
        // Reset previously found segment (fix no dialog popup)
        mIsSameSegment = false;

        mSegmentsAction = mMediaItemManager.getSponsorSegmentsObserve(item.videoId, mContentBlockData.getEnabledCategories())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        segments -> {
                            mVideo = item;
                            mSponsorSegments = segments;
                            if (mContentBlockData.isColorMarkersEnabled()) {
                                getController().setSeekBarSegments(toSeekBarSegments(segments));
                            }
                            if (mContentBlockData.isActionsEnabled()) {
                                startPlaybackWatcher();
                            }
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
                .subscribeOn(Schedulers.io())
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
                Integer resId = mContentBlockData.getLocalizedRes(segment.getCategory());
                String localizedCategory = resId != null ? getActivity().getString(resId) : segment.getCategory();

                int type = mContentBlockData.getAction(segment.getCategory());

                if (type == ContentBlockData.ACTION_SKIP_ONLY || getController().isInPIPMode()) {
                    setPositionMs(segment.getEndMs());
                } else if (type == ContentBlockData.ACTION_SKIP_WITH_TOAST) {
                    messageSkip(segment.getEndMs(), localizedCategory);
                } else if (type == ContentBlockData.ACTION_SHOW_DIALOG) {
                    confirmSkip(segment.getEndMs(), localizedCategory);
                }

                break;
            }
        }

        // Skip each segment only once
        if (foundSegment != null && mContentBlockData.isSkipEachSegmentOnceEnabled()) {
            mSponsorSegments.remove(foundSegment);
        }

        mIsSameSegment = isSegmentFound;
    }

    private boolean isPositionAtSegmentStart(long positionMs, SponsorSegment segment) {
        // Note. Getting into account playback speed. Also check that the zone is long enough.
        //float checkEndMs = segment.getStartMs() + SEGMENT_CHECK_LENGTH_MS * getController().getSpeed();
        //return positionMs >= segment.getStartMs() && positionMs <= checkEndMs && checkEndMs <= segment.getEndMs();
        return positionMs >= segment.getStartMs() && positionMs <= segment.getEndMs();
    }

    private boolean isPositionInsideSegment(long positionMs, SponsorSegment segment) {
        return positionMs >= segment.getStartMs() && positionMs < segment.getEndMs();
    }

    private void messageSkip(long skipPositionMs, String category) {
        MessageHelpers.showMessage(getActivity(),
                String.format("%s: %s", ContentBlockData.SPONSOR_BLOCK_NAME, getActivity().getString(R.string.msg_skipping_segment, category)));
        setPositionMs(skipPositionMs);
    }

    private void confirmSkip(long skipPositionMs, String category) {
        if (mIsSameSegment) {
            return;
        }

        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getActivity());
        settingsPresenter.clear();

        OptionItem acceptOption = UiOptionItem.from(
                getActivity().getString(R.string.confirm_segment_skip, category),
                option -> {
                    settingsPresenter.closeDialog();
                    setPositionMs(skipPositionMs);
                }
        );

        OptionItem cancelOption = UiOptionItem.from(
                getActivity().getString(R.string.cancel_dialog),
                option -> settingsPresenter.closeDialog()
        );

        settingsPresenter.appendSingleButton(acceptOption);
        settingsPresenter.appendSingleButton(cancelOption);
        settingsPresenter.setCloseTimeoutMs(skipPositionMs - getController().getPositionMs());

        settingsPresenter.enableTransparent(true);
        settingsPresenter.showDialog(ContentBlockData.SPONSOR_BLOCK_NAME);
    }

    private List<SeekBarSegment> toSeekBarSegments(List<SponsorSegment> segments) {
        if (segments == null) {
            return null;
        }

        List<SeekBarSegment> result = new ArrayList<>();

        for (SponsorSegment sponsorSegment : segments) {
            if (!mContentBlockData.isColorMarkerEnabled(sponsorSegment.getCategory())) {
                continue;
            }

            SeekBarSegment seekBarSegment = new SeekBarSegment();
            double startRatio = (double) sponsorSegment.getStartMs() / getController().getLengthMs(); // Range: [0, 1]
            double endRatio = (double) sponsorSegment.getEndMs() / getController().getLengthMs(); // Range: [0, 1]
            seekBarSegment.startProgress = (int) (startRatio * Integer.MAX_VALUE); // Could safely cast to int
            seekBarSegment.endProgress = (int) (endRatio * Integer.MAX_VALUE); // Could safely cast to int
            seekBarSegment.color = ContextCompat.getColor(getActivity(), mContentBlockData.getColorRes(sponsorSegment.getCategory()));
            result.add(seekBarSegment);
        }

        return result;
    }

    /**
     * Sponsor block fix. Position may exceed real media length.
     */
    private void setPositionMs(long positionMs) {
        long lengthMs = getController().getLengthMs();

        // Sponsor block fix. Position may exceed real media length.
        if (lengthMs > 0 && positionMs > lengthMs) {
            positionMs = lengthMs;
        }

        getController().setPositionMs(positionMs);
    }
}
