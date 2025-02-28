package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerUI;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.ContentBlockSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.ContentBlockData;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ContentBlockController extends BasePlayerController {
    private static final String TAG = ContentBlockController.class.getSimpleName();
    private static final long POLL_INTERVAL_MS = 1_000;
    private static final int CONTENT_BLOCK_ID = 144;
    private MediaItemService mMediaItemService;
    private ContentBlockData mContentBlockData;
    private Video mVideo;
    private List<SponsorSegment> mOriginalSegments;
    private List<SponsorSegment> mActiveSegments;
    private long mLastSkipPosMs;
    private boolean mSkipExclude;
    private Disposable mSegmentsAction;
    private Observable<List<SponsorSegment>> mCachedSegmentsAction;

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

        @NonNull
        @Override
        public String toString() {
            return String.format("%s,%s", segmentCategory, actionType);
        }
    }

    @Override
    public void onInit() {
        ServiceManager service = YouTubeServiceManager.instance();
        mMediaItemService = service.getMediaItemService();
        mContentBlockData = ContentBlockData.instance(getContext());
    }

    @Override
    public void onNewVideo(Video item) {
        mSkipExclude = false;
        if (getPlayer() != null) {
            getPlayer().setSeekBarSegments(null); // reset colors
        }
    }

    @Override
    public void onVideoLoaded(Video item) {
        disposeActions();

        boolean enabled = mContentBlockData.isSponsorBlockEnabled() && checkVideo(item) && !isChannelExcluded(item.channelId);
        getPlayer().setButtonState(R.id.action_content_block, enabled ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);

        if (enabled) {
            updateSponsorSegmentsAndWatch(item);
        }
    }

    @Override
    public void onMetadata(MediaItemMetadata metadata) {
        // Disable sponsor for the live streams.
        // Fix when using remote control.
        if (!mContentBlockData.isSponsorBlockEnabled() || !checkVideo(getPlayer().getVideo())) {
            disposeActions();
        } else if (isChannelExcluded(metadata.getChannelId())) { // got channel id. check the exclusions
            getPlayer().setButtonState(R.id.action_content_block, PlayerUI.BUTTON_OFF);
            disposeActions();
        }
    }

    @Override
    public void onEngineReleased() {
        disposeActions();
    }

    @Override
    public void onButtonClicked(int buttonId, int buttonState) {
        if (buttonId == R.id.action_content_block) {
            List<SponsorSegment> foundSegments = findMatchedSegments(getPlayer().getPositionMs(), mOriginalSegments, true);

            if (foundSegments != null) {
                SponsorSegment lastSegment = foundSegments.get(foundSegments.size() - 1);
                setPositionMs(lastSegment.getEndMs());
                return;
            }
        }
    }

    @Override
    public void onButtonLongClicked(int buttonId, int buttonState) {
        if (buttonId == R.id.action_content_block) {
            ContentBlockSettingsPresenter.instance(getContext()).show(() -> {
                if (getPlayer() != null) {
                    onVideoLoaded(getPlayer().getVideo());
                }
            });
        }
    }

    private boolean checkVideo(Video video) {
        //return video != null && !video.isLive && !video.isUpcoming;
        return video != null;
    }

    private void updateSponsorSegmentsAndWatch(Video item) {
        if (item == null || item.videoId == null || item.isLive || mContentBlockData.getEnabledCategories().isEmpty()) {
            mActiveSegments = mOriginalSegments = null;
            mCachedSegmentsAction = null;
            return;
        }

        if (!item.equals(mVideo) || mCachedSegmentsAction == null) {
            // NOTE: SponsorBlock (when happened java.net.SocketTimeoutException) could block whole application with Schedulers.io()
            // Because Schedulers.io() reuses blocked threads in RxJava 2: https://github.com/ReactiveX/RxJava/issues/6542
            mCachedSegmentsAction = mMediaItemService.getSponsorSegmentsObserve(item.videoId, mContentBlockData.getEnabledCategories())
                    .cache();
            mVideo = item;
        }

        mSegmentsAction = mCachedSegmentsAction
                .flatMap(this::startSponsorWatcher)
                .subscribe(
                        this::skipSegment,
                        error -> Log.d(TAG, "It's ok. Nothing to block in this video. Error msg: %s", error.getMessage())
                );
    }

    private Observable<Long> startSponsorWatcher(List<SponsorSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            mActiveSegments = mOriginalSegments = null;
            return Observable.empty();
        }

        mOriginalSegments = segments;

        mActiveSegments = new ArrayList<>(segments);

        if (mContentBlockData.isColorMarkersEnabled()) {
            getPlayer().setSeekBarSegments(toSeekBarSegments(segments));
        }
        if (mContentBlockData.isActionsEnabled()) {
            // Warn. Try to not access player object here.
            // Or you'll get "Player is accessed on the wrong thread" error.
            return RxHelper.interval(POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
        } else {
            return Observable.empty();
        }
    }

    private void disposeActions() {
        RxHelper.disposeActions(mSegmentsAction);

        // Note, removes all segments at once
        //getPlayer().setSeekBarSegments(null); // reset colors

        // Reset previously found segment (fix no dialog popup)
        mLastSkipPosMs = 0;
    }

    private void skipSegment(long interval) {
        if (mActiveSegments == null || mActiveSegments.isEmpty() || !Video.equals(mVideo, getPlayer().getVideo())) {
            disposeActions();
            return;
        }

        // Fix looping messages at the end of the video (playback mode: pause at the end of the video)
        if (!getPlayer().isPlaying()) {
            return;
        }

        long positionMs = getPlayer().getPositionMs();

        List<SponsorSegment> foundSegments = findMatchedSegments(positionMs, mActiveSegments, false);

        applyActions(foundSegments);

        // Skip each segment only once
        if (foundSegments != null && mContentBlockData.isDontSkipSegmentAgainEnabled()) {
            mActiveSegments.removeAll(foundSegments);
        }
    }

    private boolean isPositionInsideSegment(long positionMs, SponsorSegment segment, boolean fullMatch) {
        // NOTE: in case of using Player.setSeekParameters (inaccurate seeking) increase sponsor segment window
        // int seekShift = 1_000;
        // return positionMs >= (segment.getStartMs() - seekShift) && positionMs <= (segment.getEndMs() + seekShift);

        if (fullMatch) {
            return positionMs >= segment.getStartMs() && positionMs <= segment.getEndMs();
        } else {
            long windowSizeMs = (long) (2_000 * getPlayer().getSpeed());
            return positionMs >= segment.getStartMs() && positionMs <= Math.min(segment.getStartMs() + windowSizeMs, segment.getEndMs());
        }
    }

    private void simpleSkip(long skipPosMs) {
        if (mLastSkipPosMs == skipPosMs) {
            return;
        }

        setPositionMs(skipPosMs);
        closeTransparentDialog();
    }

    private void messageSkip(long skipPosMs, String category) {
        if (mLastSkipPosMs == skipPosMs) {
            return;
        }

        MessageHelpers.showMessage(getContext(),
                String.format("%s: %s", getContext().getString(R.string.content_block_provider), getContext().getString(R.string.msg_skipping_segment, category)));
        setPositionMs(skipPosMs);
        closeTransparentDialog();
    }

    private void confirmSkip(long skipPosMs, String category) {
        if (mLastSkipPosMs == skipPosMs) {
            return;
        }

        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(getContext());

        if (dialogPresenter.isDialogShown() || getPlayer().isSuggestionsShown()) {
            // Another dialog is opened. Don't distract a user.
            return;
        }

        getPlayer().showControls(false);

        OptionItem acceptOption = UiOptionItem.from(
                getContext().getString(R.string.confirm_segment_skip, category),
                option -> {
                    // return to previous dialog or close if no other dialogs in stack
                    dialogPresenter.goBack();
                    setPositionMs(skipPosMs);
                }
        );

        dialogPresenter.appendSingleButton(acceptOption);
        dialogPresenter.setCloseTimeoutMs((long) ((skipPosMs - getPlayer().getPositionMs()) * getPlayer().getSpeed()));

        dialogPresenter.enableTransparent(true);
        dialogPresenter.enableOverlay(true);
        dialogPresenter.enableExpandable(false);
        dialogPresenter.setId(CONTENT_BLOCK_ID);
        dialogPresenter.showDialog(getContext().getString(R.string.content_block_provider));
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
            float startRatio = (float) sponsorSegment.getStartMs() / getPlayer().getDurationMs(); // Range: [0, 1]
            float endRatio = (float) sponsorSegment.getEndMs() / getPlayer().getDurationMs(); // Range: [0, 1]
            seekBarSegment.startProgress = startRatio;
            seekBarSegment.endProgress = endRatio;
            seekBarSegment.color = ContextCompat.getColor(getContext(), mContentBlockData.getColorRes(sponsorSegment.getCategory()));
            result.add(seekBarSegment);
        }

        return result;
    }

    /**
     * Sponsor block fix. Position may exceed real media length.
     */
    private void setPositionMs(long positionMs) {
        long durationMs = getPlayer().getDurationMs();

        // Sponsor block fix. Position may exceed real media length.
        getPlayer().setPositionMs(Math.min(positionMs, durationMs));
    }

    /**
     * @param fullMatch Match only the beginning or the full segment length
     */
    private List<SponsorSegment> findMatchedSegments(long positionMs, List<SponsorSegment> segments, boolean fullMatch) {
        if (segments == null) {
            return null;
        }

        List<SponsorSegment> foundSegment = null;

        for (SponsorSegment segment : segments) {
            int action = mContentBlockData.getAction(segment.getCategory());
            boolean isSkipAction = action == ContentBlockData.ACTION_SKIP_ONLY ||
                    action == ContentBlockData.ACTION_SKIP_WITH_TOAST;
            if (foundSegment == null) {
                if (isPositionInsideSegment(positionMs, segment, fullMatch)) {
                    foundSegment = new ArrayList<>();
                    foundSegment.add(segment);

                    // Action grouping aren't supported for dialogs
                    if (!isSkipAction) {
                        break;
                    }
                }
            } else {
                SponsorSegment lastSegment = foundSegment.get(foundSegment.size() - 1);
                if (isSkipAction && isPositionInsideSegment(lastSegment.getEndMs() + 3_000, segment, fullMatch)) {
                    foundSegment.add(segment);
                }
            }
        }

        return foundSegment;
    }

    private void applyActions(List<SponsorSegment> foundSegments) {
        if (foundSegments != null) {
            SponsorSegment lastSegment = foundSegments.get(foundSegments.size() - 1);

            Integer resId = mContentBlockData.getLocalizedRes(lastSegment.getCategory());
            String skipMessage = resId != null ? getContext().getString(resId) : lastSegment.getCategory();

            int type = mContentBlockData.getAction(lastSegment.getCategory());

            long skipPosMs = lastSegment.getEndMs();

            if (type == ContentBlockData.ACTION_SKIP_ONLY || getPlayer().isInPIPMode() || Utils.isScreenOff(getContext())) {
                simpleSkip(skipPosMs);
            } else if (type == ContentBlockData.ACTION_SKIP_WITH_TOAST) {
                messageSkip(skipPosMs, skipMessage);
            } else if (type == ContentBlockData.ACTION_SHOW_DIALOG) {
                confirmSkip(skipPosMs, skipMessage);
            }
        }

        mLastSkipPosMs = foundSegments != null ? foundSegments.get(foundSegments.size() - 1).getEndMs() : 0;
    }

    private void closeTransparentDialog() {
        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(getContext());

        if (dialogPresenter.isDialogShown() && dialogPresenter.getId() == CONTENT_BLOCK_ID) {
            dialogPresenter.closeDialog();
        }
    }

    private boolean isChannelExcluded(String channelId) {
        return !mSkipExclude && mContentBlockData.isChannelExcluded(channelId);
    }
}
