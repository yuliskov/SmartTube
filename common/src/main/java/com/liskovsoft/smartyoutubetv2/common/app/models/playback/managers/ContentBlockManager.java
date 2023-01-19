package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import androidx.core.content.ContextCompat;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
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
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.common.misc.ScreensaverManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.ContentBlockData;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ContentBlockManager extends PlayerEventListenerHelper implements MetadataListener {
    private static final String TAG = ContentBlockManager.class.getSimpleName();
    private MediaItemService mMediaItemManager;
    private ContentBlockData mContentBlockData;
    private Video mVideo;
    private List<SponsorSegment> mSponsorSegments;
    private Disposable mProgressAction;
    private Disposable mSegmentsAction;
    private long mLastSkipPosMs;

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
        mMediaItemManager = mediaService.getMediaItemService();
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
    public void onEngineInitialized() {
        getController().setContentBlockButtonState(mContentBlockData.isSponsorBlockEnabled());
    }

    @Override
    public void onEngineReleased() {
        disposeActions();
    }

    @Override
    public void onContentBlockClicked(boolean enabled) {
        mContentBlockData.enableSponsorBlock(enabled);
        onVideoLoaded(getController().getVideo());
    }

    private boolean checkVideo(Video video) {
        return video != null && !video.isLive && !video.isUpcoming;
    }

    private void updateSponsorSegmentsAndWatch(Video item) {
        if (item == null || item.videoId == null || mContentBlockData.getEnabledCategories().isEmpty()) {
            mSponsorSegments = null;
            return;
        }

        if (item.equals(mVideo)) {
            startSponsorWatcher();
            return;
        }

        // NOTE: SponsorBlock (when happened java.net.SocketTimeoutException) could block whole application with Schedulers.io()
        // Because Schedulers.io() reuses blocked threads in RxJava 2: https://github.com/ReactiveX/RxJava/issues/6542
        mSegmentsAction = mMediaItemManager.getSponsorSegmentsObserve(item.videoId, mContentBlockData.getEnabledCategories())
                .subscribe(
                        segments -> {
                            mVideo = item;
                            mSponsorSegments = segments;
                            startSponsorWatcher();
                        },
                        error -> {
                            Log.d(TAG, "It's ok. Nothing to block in this video. Error msg: %s", error.getMessage());
                        }
                );
    }

    private void startSponsorWatcher() {
        if (mSponsorSegments == null) {
            return;
        }

        if (mContentBlockData.isColorMarkersEnabled()) {
            getController().setSeekBarSegments(toSeekBarSegments(mSponsorSegments));
        }
        if (mContentBlockData.isActionsEnabled()) {
            startPlaybackWatcher();
        }
    }

    private void startPlaybackWatcher() {
        // Warn. Try to not access player object here.
        // Or you'll get "Player is accessed on the wrong thread" error.
        Observable<Long> playbackProgressObservable =
                RxHelper.interval(1, TimeUnit.SECONDS);

        mProgressAction = playbackProgressObservable
                .subscribe(
                        this::skipSegment,
                        error -> Log.e(TAG, "startPlaybackWatcher error: %s", error.getMessage())
                );
    }

    private void disposeActions() {
        RxHelper.disposeActions(mProgressAction, mSegmentsAction);
        //mSponsorSegments = null;
        //mVideo = null;

        // Reset colors
        //getController().setSeekBarSegments(null);
        // Reset previously found segment (fix no dialog popup)
        mLastSkipPosMs = 0;
    }

    private void skipSegment(long interval) {
        if (mSponsorSegments == null || !Video.equals(mVideo, getController().getVideo())) {
            return;
        }

        // Fix looping messages at the end of the video (playback mode: pause at the end of the video)
        if (!getController().isPlaying()) {
            return;
        }

        long positionMs = getController().getPositionMs();

        List<SponsorSegment> foundSegment = findMatchedSegments(positionMs);

        applyActions(foundSegment);

        // Skip each segment only once
        if (foundSegment != null && mContentBlockData.isSkipEachSegmentOnceEnabled()) {
            mSponsorSegments.removeAll(foundSegment);
        }
    }

    private boolean isPositionInsideSegment(long positionMs, SponsorSegment segment) {
        // NOTE: in case of using Player.setSeekParameters (inaccurate seeking) increase sponsor segment window
        // int seekShift = 1_000;
        // return positionMs >= (segment.getStartMs() - seekShift) && positionMs <= (segment.getEndMs() + seekShift);
        return positionMs >= segment.getStartMs() && positionMs <= segment.getEndMs();
    }

    private void simpleSkip(long skipPosMs) {
        if (mLastSkipPosMs == skipPosMs) {
            return;
        }

        setPositionMs(skipPosMs);
    }

    private void messageSkip(long skipPosMs, String category) {
        if (mLastSkipPosMs == skipPosMs) {
            return;
        }

        MessageHelpers.showMessage(getActivity(),
                String.format("%s: %s", getActivity().getString(R.string.content_block_provider), getActivity().getString(R.string.msg_skipping_segment, category)));
        setPositionMs(skipPosMs);
    }

    private void confirmSkip(long skipPosMs, String category) {
        if (mLastSkipPosMs == skipPosMs) {
            return;
        }

        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getActivity());

        OptionItem acceptOption = UiOptionItem.from(
                getActivity().getString(R.string.confirm_segment_skip, category),
                option -> {
                    settingsPresenter.closeDialog();
                    setPositionMs(skipPosMs);
                }
        );

        OptionItem cancelOption = UiOptionItem.from(
                getActivity().getString(R.string.cancel_dialog),
                option -> settingsPresenter.closeDialog()
        );

        settingsPresenter.appendSingleButton(acceptOption);
        settingsPresenter.appendSingleButton(cancelOption);
        settingsPresenter.setCloseTimeoutMs(skipPosMs - getController().getPositionMs());

        settingsPresenter.enableTransparent(true);
        settingsPresenter.showDialog(getActivity().getString(R.string.content_block_provider));
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
            double startRatio = (double) sponsorSegment.getStartMs() / getController().getDurationMs(); // Range: [0, 1]
            double endRatio = (double) sponsorSegment.getEndMs() / getController().getDurationMs(); // Range: [0, 1]
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
        long durationMs = getController().getDurationMs();

        // Sponsor block fix. Position may exceed real media length.
        getController().setPositionMs(Math.min(positionMs, durationMs));
    }

    private List<SponsorSegment> findMatchedSegments(long positionMs) {
        if (mSponsorSegments == null) {
            return null;
        }

        List<SponsorSegment> foundSegment = null;

        for (SponsorSegment segment : mSponsorSegments) {
            int action = mContentBlockData.getAction(segment.getCategory());
            boolean isSkipAction = action == ContentBlockData.ACTION_SKIP_ONLY ||
                    action == ContentBlockData.ACTION_SKIP_WITH_TOAST;
            if (foundSegment == null) {
                if (isPositionInsideSegment(positionMs, segment)) {
                    foundSegment = new ArrayList<>();
                    foundSegment.add(segment);

                    // Action grouping aren't supported for dialogs
                    if (!isSkipAction) {
                        break;
                    }
                }
            } else {
                SponsorSegment lastSegment = foundSegment.get(foundSegment.size() - 1);
                if (isSkipAction && isPositionInsideSegment(lastSegment.getEndMs() + 3_000, segment)) {
                    foundSegment.add(segment);
                }
            }
        }

        return foundSegment;
    }

    private void applyActions(List<SponsorSegment> foundSegment) {
        if (foundSegment != null) {
            SponsorSegment lastSegment = foundSegment.get(foundSegment.size() - 1);

            Integer resId = mContentBlockData.getLocalizedRes(lastSegment.getCategory());
            String localizedCategory = resId != null ? getActivity().getString(resId) : lastSegment.getCategory();

            int type = mContentBlockData.getAction(lastSegment.getCategory());

            long skipPosMs = lastSegment.getEndMs();

            if (type == ContentBlockData.ACTION_SKIP_ONLY || getController().isInPIPMode() || isScreenOff()) {
                simpleSkip(skipPosMs);
            } else if (type == ContentBlockData.ACTION_SKIP_WITH_TOAST) {
                messageSkip(skipPosMs, localizedCategory);
            } else if (type == ContentBlockData.ACTION_SHOW_DIALOG) {
                confirmSkip(skipPosMs, localizedCategory);
            }
        }

        mLastSkipPosMs = foundSegment != null ? foundSegment.get(foundSegment.size() - 1).getEndMs() : 0;
    }

    private boolean isScreenOff() {
        ScreensaverManager manager = ((MotherActivity) getActivity()).getScreensaverManager();

        return manager != null && manager.isScreenOff();
    }
}
