package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import androidx.core.content.ContextCompat;
import com.liskovsoft.mediaserviceinterfaces.yt.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.yt.data.ChapterItem;
import com.liskovsoft.mediaserviceinterfaces.yt.data.DislikeData;
import com.liskovsoft.mediaserviceinterfaces.yt.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.yt.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.DeArrowProcessor;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

import java.util.ArrayList;
import java.util.List;

public class SuggestionsController extends PlayerEventListenerHelper {
    private static final String TAG = SuggestionsController.class.getSimpleName();
    private final List<Disposable> mActions = new ArrayList<>();
    private PlayerTweaksData mPlayerTweaksData;
    private MediaItemService mMediaItemService;
    private DeArrowProcessor mDeArrowProcessor;
    private Video mNextVideo;
    private int mFocusCount;
    private int mNextRetryCount;
    private List<ChapterItem> mChapters;
    private final Runnable mChapterHandler = this::startChapterNotificationServiceIfNeededInt;
    private static final int MAX_PLAYLIST_CONTINUATIONS = 20;
    private static final int CHAPTER_NOTIFICATION_Id = 565;

    private interface OnVideoGroup {
        void onVideoGroup(VideoGroup group);
    }

    private interface OnMetadata {
        void onMetadata(MediaItemMetadata metadata);
    }

    @Override
    public void onInit() {
        mPlayerTweaksData = PlayerTweaksData.instance(getContext());
        mDeArrowProcessor = new DeArrowProcessor(getContext(), PlaybackPresenter.instance(getContext())::syncItem);
        mMediaItemService = YouTubeServiceManager.instance().getMediaItemService();
    }

    @Override
    public void openVideo(Video video) {
        // Remote control fix. Slow network fix. Suggestions may still be loading.
        // This could lead to changing current video info (title, id etc) to wrong one.
        disposeActions();
        //mCurrentGroup = video.getGroup(); // disable garbage collected
        appendNextSectionVideoIfNeeded(video);
    }

    /**
     * Improve video load time by running a fetch after load event
     */
    @Override
    public void onVideoLoaded(Video item) {
        loadSuggestions(item);
    }

    // Could make negative impact on the video load time.
    //@Override
    //public void onSourceChanged(Video item) {
    //    loadSuggestions(item);
    //}

    @Override
    public void onEngineReleased() {
        disposeActions();
    }

    @Override
    public void onFinish() {
        disposeActions();
    }

    @Override
    public void onScrollEnd(Video item) {
        if (item == null) {
            Log.e(TAG, "Can't scroll. Video is null.");
            return;
        }

        VideoGroup group = item.getGroup();

        continueGroup(group);
    }

    @Override
    public void onSuggestionItemClicked(Video item) {
        markAsQueueIfNeeded(item);
    }

    @Override
    public void onControlsShown(boolean shown) {
        if (shown) {
            focusCurrentChapter();
        } else {
            startChapterNotificationServiceIfNeeded();
        }
    }

    @Override
    public void onSeekEnd() {
        if (getPlayer().isControlsShown()) {
            focusCurrentChapter();
        } else {
            startChapterNotificationServiceIfNeeded();
        }
    }

    @Override
    public void onTickle() {
        updateLiveDescription();
    }

    private void updateLiveDescription() {
        if (getPlayer() == null) {
            return;
        }

        Video video = getPlayer().getVideo();

        if (video == null || !video.isLive || RxHelper.isAnyActionRunning(mActions)) {
            return;
        }

        loadMetadata(video, metadata -> {
            if (video.isLive != metadata.isLive()) {
                video.isLiveEnd = true;
            }

            syncCurrentVideo(metadata, video);
        });
    }

    private void continueGroup(VideoGroup group) {
        continueGroup(group, null, true);
    }

    private void continueGroup(VideoGroup group, boolean showLoading) {
        continueGroup(group, null, showLoading);
    }

    private void continueGroup(VideoGroup group, OnVideoGroup callback, boolean showLoading) {
        if (group == null) {
            Log.e(TAG, "Can't continue group. The group is null.");
            return;
        }

        Log.d(TAG, "continueGroup: start continue group: " + group.getTitle());

        if (showLoading) {
            getPlayer().showProgressBar(true);
        }

        MediaGroup mediaGroup = group.getMediaGroup();

        Disposable continueAction = mMediaItemService.continueGroupObserve(mediaGroup)
                .subscribe(
                        continueMediaGroup -> {
                            getPlayer().showProgressBar(false);

                            VideoGroup videoGroup = VideoGroup.from(group, continueMediaGroup);
                            getPlayer().updateSuggestions(videoGroup);
                            mDeArrowProcessor.process(videoGroup);

                            mergeUserAndRemoteQueue(videoGroup);

                            if (callback != null) {
                                callback.onVideoGroup(videoGroup);
                            } else {
                                continueGroupIfNeeded(videoGroup);
                            }
                        },
                        error -> {
                            Log.e(TAG, "continueGroup error: %s", error.getMessage());
                            if (getPlayer() != null) {
                                getPlayer().showProgressBar(false);
                            }
                        }
                );

        mActions.add(continueAction);
    }

    private void syncCurrentVideo(MediaItemMetadata mediaItemMetadata, Video video) {
        //if (getPlayer().containsMedia()) {
        //    video.isUpcoming = false; // live stream started
        //}

        // NOTE: Skip upcoming or unplayable (no media) because default title more informative (e.g. has scheduled time).
        // NOTE: Upcoming videos metadata wrongly reported as live
        if (!getPlayer().containsMedia()) {
            return;
        }

        video.sync(mediaItemMetadata);
        getPlayer().setVideo(video);

        getPlayer().setNextTitle(getNext() != null ? getNext().getTitle() : null);

        appendDislikes(video);
    }

    public void loadSuggestions(Video video) {
        clearSuggestionsIfNeeded(video);
        loadMetadata(video, metadata -> updateSuggestions(metadata, video));
    }

    private void loadMetadata(Video video, OnMetadata callback) {
        disposeActions();

        if (video == null) {
            Log.e(TAG, "loadSuggestions: video is null");
            return;
        }

        Observable<MediaItemMetadata> observable;

        // NOTE: Load suggestions from mediaItem isn't robust. Because playlistId may be initialized from RemoteControlManager.
        // Video might be loaded from Channels section (has playlistParams)
        observable = mMediaItemService.getMetadataObserve(video.videoId, video.getPlaylistId(), video.playlistIndex, video.playlistParams);

        Disposable metadataAction = observable
                .subscribe(
                        callback::onMetadata,
                        error -> {
                            MessageHelpers.showLongMessage(getContext(), "loadSuggestions error: %s", error.getMessage());
                            Log.e(TAG, "loadSuggestions error: %s", error.getMessage());
                            error.printStackTrace();
                            // Errors are usual here (something with title parsing)
                        }
                );

        mActions.add(metadataAction);
    }

    public Video getNext() {
        Video result = null;
        Video next = Playlist.instance().getNext();
        Video current = getPlayer().getVideo();

        if (next != null) {
            next.fromQueue = true;
            result = next;
        } else if (mNextVideo != null) {
            result = mNextVideo;
        } else if (current != null && current.nextMediaItem != null) {
            result = Video.from(current.nextMediaItem);
        }

        return result;
    }

    public Video getPrevious() {
        Video result = getPreviousFromGroup(getPlayer().getVideo());

        if (result == null) {
            Video previous = Playlist.instance().getPrevious();

            if (previous != null) {
                previous.fromQueue = true;
                result = previous;
            }
        }

        return result;
    }

    private Video getPreviousFromGroup(Video current) {
        Video result = null;

        if (current != null) {
            VideoGroup group = current.getGroup();

            if (group != null && !group.isEmpty()) {
                Video previous = null;

                for (Video item : group.getVideos()) {
                    if (item.equals(current)) {
                        result = previous;
                        break;
                    }

                    if (item.hasVideo() && !item.isUpcoming) {
                        previous = item;
                    }
                }
            }
        }

        return result;
    }

    private void clearSuggestionsIfNeeded(Video video) {
        if (video == null || getPlayer() == null) {
            return;
        }

        // Frees a lot of memory
        if (video.isRemote || !getPlayer().isSuggestionsShown()) {
            getPlayer().clearSuggestions();
        }
    }

    private void updateSuggestions(MediaItemMetadata mediaItemMetadata, Video video) {
        syncCurrentVideo(mediaItemMetadata, video);

        appendSuggestions(video, mediaItemMetadata);

        // After video suggestions
        callListener(mediaItemMetadata);
    }

    private void appendSuggestions(Video video, MediaItemMetadata mediaItemMetadata) {
        if (!video.isRemote && getPlayer().isSuggestionsShown()) {
            Log.d(TAG, "Suggestions is opened. Seems that user want to stay here.");
            return;
        }

        getPlayer().clearSuggestions(); // clear previous videos

        appendChaptersIfNeeded(mediaItemMetadata);

        mergeUserAndRemoteQueueIfNeeded(video, mediaItemMetadata);

        appendSectionPlaylistIfNeeded(video);

        List<MediaGroup> suggestions = mediaItemMetadata.getSuggestions();

        if (suggestions == null) {
            String msg = "loadSuggestions: Can't obtain suggestions for video: " + video.getTitle();
            Log.e(TAG, msg);
            return;
        }

        int groupIndex = -1;
        int suggestRows = -1;

        if (GeneralData.instance(getContext()).isChildModeEnabled() || mPlayerTweaksData.isSuggestionsDisabled()) {
            suggestRows = video.hasPlaylist() ? 1 : 0;
        }

        for (MediaGroup group : suggestions) {
            groupIndex++;

            if (groupIndex == suggestRows) {
                break;
            }

            if (group != null && !group.isEmpty()) {
                VideoGroup videoGroup = VideoGroup.from(group);

                //if (groupIndex == 0) {
                //    mergeRemoteAndUserQueueIfNeeded(video, videoGroup);
                //}

                getPlayer().updateSuggestions(videoGroup);
                mDeArrowProcessor.process(videoGroup);

                if (groupIndex == 0) {
                    focusAndContinueIfNeeded(videoGroup);
                } else {
                    continueGroupIfNeeded(videoGroup);
                }
            }
        }
    }

    /**
     * Merge remote queue with player's queue (when phone cast just started or user clicked on playlist item)
     */
    private void mergeUserAndRemoteQueueIfNeeded(Video video, MediaItemMetadata metadata) {
        // Ensure that the user pressed video thumb on the phone
        if (video.isRemote && video.remotePlaylistId != null) {
            // Create user queue from remote queue

            List<MediaGroup> suggestions = metadata.getSuggestions();

            if (suggestions != null && !suggestions.isEmpty()) {
                MediaGroup remoteRow = suggestions.get(0);

                VideoGroup remoteGroup = VideoGroup.from(remoteRow);

                suggestions.remove(remoteRow);

                appendRemoteQueueIfNeeded(video, remoteGroup);
            }
        } else {
            appendUserQueueIfNeeded();
        }
    }

    private void mergeUserAndRemoteQueue(VideoGroup videoGroup) {
        Video video = getPlayer().getVideo();
        if (videoGroup.isQueue) {
            Playlist.instance().addAll(videoGroup.getVideos());
            Playlist.instance().setCurrent(video);
        }
    }

    private void appendUserQueueIfNeeded() {
        Playlist playlist = Playlist.instance();

        if (playlist.hasNext()) {
            List<Video> queue = playlist.getAllAfterCurrent();

            VideoGroup videoGroup = VideoGroup.from(queue);
            videoGroup.setTitle(getContext().getString(R.string.action_playback_queue));
            videoGroup.setId(videoGroup.getTitle().hashCode());

            getPlayer().updateSuggestions(videoGroup);
        }
    }

    private void appendRemoteQueueIfNeeded(Video video, VideoGroup remoteGroup) {
        remoteGroup.removeAllBefore(video);
        remoteGroup.stripPlaylistInfo(); // prefer user queue even when a phone disconnected

        if (remoteGroup.contains(video)) {
            Playlist playlist = Playlist.instance();
            playlist.removeAllAfterCurrent();
            playlist.addAll(remoteGroup.getVideos());
            playlist.setCurrent(video);
        }

        remoteGroup.setTitle(getContext().getString(R.string.action_playback_queue));
        remoteGroup.setId(remoteGroup.getTitle().hashCode());
        remoteGroup.isQueue = true;

        remoteGroup.setAction(VideoGroup.ACTION_REPLACE);
        getPlayer().updateSuggestions(remoteGroup);

        if (!remoteGroup.contains(video) && remoteGroup.getSize() < 100) {
            continueGroup(remoteGroup, group -> appendRemoteQueueIfNeeded(video, group), false);
        }
    }

    private void addChapterMarkersIfNeeded() {
        if (mChapters == null) {
            return;
        }

        getPlayer().setSeekBarSegments(toSeekBarSegments(mChapters));
    }

    private void appendChapterSuggestionsIfNeeded() {
        if (mChapters == null) {
            return;
        }

        VideoGroup videoGroup = VideoGroup.fromChapters(mChapters, getContext().getString(R.string.chapters));

        getPlayer().updateSuggestions(videoGroup);
    }

    private void startChapterNotificationServiceIfNeeded() {
        if (mPlayerTweaksData.isChapterNotificationEnabled()) {
            Utils.postDelayed(mChapterHandler, 1_000); // small delay to give a chance to complete dialog transitions
        }
    }

    private void startChapterNotificationServiceIfNeededInt() {
        Utils.removeCallbacks(mChapterHandler);

        showChapterDialog(getCurrentChapter());

        if (mChapters == null) {
            return;
        }

        long positionMs = getPlayer().getPositionMs();

        ChapterItem chapter = getNextChapter();

        if (chapter != null) {
            Utils.postDelayed(mChapterHandler, (long) ((chapter.getStartTimeMs() - positionMs) * getPlayer().getSpeed()));
        }
    }

    private void appendChaptersIfNeeded(MediaItemMetadata mediaItemMetadata) {
        mChapters = mediaItemMetadata.getChapters();

        addChapterMarkersIfNeeded();
        appendChapterSuggestionsIfNeeded();
        startChapterNotificationServiceIfNeeded();
        focusCurrentChapter();
    }

    private void appendSectionPlaylistIfNeeded(Video video) {
        if (!isSectionPlaylistEnabled(video)) {
            // Important fix. Gives priority to playlist or suggestion.
            mNextVideo = null;
            return;
        }

        getPlayer().updateSuggestions(video.getGroup());
        focusAndContinueIfNeeded(video.getGroup());
    }

    private void markAsQueueIfNeeded(Video item) {
        List<Video> afterCurrent = Playlist.instance().getAllAfterCurrent();

        if (afterCurrent != null && afterCurrent.contains(item)) {
            item.fromQueue = true;
        }
    }

    private void focusCurrentChapter() {
        // Reset chapter title
        getPlayer().setSeekPreviewTitle(mChapters != null ? "..." : null); // add placeholder to fix control panel animation on the first run

        if (!getPlayer().isControlsShown()) {
            return;
        }

        VideoGroup group = getPlayer().getSuggestionsByIndex(0);

        if (group == null || group.getVideos() == null) {
            return;
        }

        int index = findCurrentChapterIndex(group.getVideos());

        if (index != -1) {
            String title = group.getVideos().get(index).getTitle();
            getPlayer().focusSuggestedItem(index);
            getPlayer().setSeekPreviewTitle(title);
        }
    }

    private int findCurrentChapterIndex(List<Video> videos) {
        if (videos == null || !videos.get(0).isChapter) {
            return -1;
        }

        int currentChapter = -1;
        long positionMs = getPlayer().getPositionMs();
        for (Video chapter : videos) {
            if (chapter.startTimeMs > positionMs) {
                break;
            }
            currentChapter++;
        }

        return currentChapter;
    }

    private List<SeekBarSegment> toSeekBarSegments(List<ChapterItem> chapters) {
        if (chapters == null) {
            return null;
        }

        List<SeekBarSegment> result = new ArrayList<>();
        long markLengthMs = getPlayer().getDurationMs() / 10000;

        for (ChapterItem chapter : chapters) {
            if (chapter.getStartTimeMs() == 0) {
                continue;
            }

            SeekBarSegment seekBarSegment = new SeekBarSegment();
            float startRatio = (float) chapter.getStartTimeMs() / getPlayer().getDurationMs(); // Range: [0, 1]
            float endRatio = (float) (chapter.getStartTimeMs() + markLengthMs) / getPlayer().getDurationMs(); // Range: [0, 1]
            seekBarSegment.startProgress = startRatio;
            seekBarSegment.endProgress = endRatio;
            seekBarSegment.color = ContextCompat.getColor(getContext(), R.color.black);
            result.add(seekBarSegment);
        }

        return result;
    }

    /**
     * Most tiny ui has 8 cards in a row or 24 in grid.
     */
    private void continueGroupIfNeeded(VideoGroup group) {
        if (MediaServiceManager.instance().shouldContinueRowGroup(getContext(), group)) {
            continueGroup(group, getPlayer().isSuggestionsShown());
        }
    }

    private void focusAndContinueIfNeeded(VideoGroup group) {
        Video video = getPlayer().getVideo();

        if (group == null || group.isEmpty() || video == null || !video.hasVideo()) {
            return;
        }

        int index = group.getVideos().indexOf(video);

        if (index >= 0) { // continuation group starts with zero index
            Log.d(TAG, "Found current video index: %s", index);
            Video found = group.getVideos().get(index);
            if (!found.isMix() || isSectionPlaylistEnabled(video)) {
                getPlayer().focusSuggestedItem(found);
            }
            mFocusCount = 0; // Stop the continuation loop
        } else if (mFocusCount > MAX_PLAYLIST_CONTINUATIONS || !video.hasPlaylist()) {
            // Stop the continuation loop. Maybe the video isn't there.
            mFocusCount = 0;
        } else {
            // load more and repeat
            continueGroup(group, this::focusAndContinueIfNeeded, getPlayer().isSuggestionsShown());
            mFocusCount++;
        }
    }

    private void appendNextSectionVideoIfNeeded(Video video) {
        mNextVideo = null;

        if (!isSectionPlaylistEnabled(video)) {
            return;
        }

        VideoGroup group = video.getGroup();

        if (group == null || group.isEmpty()) {
            return;
        }

        List<Video> videos = group.getVideos();
        boolean found = false;

        for (Video current : videos) {
            if (found && current.hasVideo() && !current.isUpcoming) {
                mNextRetryCount = 0;
                mNextVideo = current;
                return;
            }

            if (current.equals(video)) {
                found = true;
            }
        }

        if (mNextRetryCount > 0) {
            mNextRetryCount = 0;
        } else {
            continueGroup(group, continuation -> appendNextSectionVideoIfNeeded(video), getPlayer().isSuggestionsShown());
            mNextRetryCount++;
        }
    }

    private void showChapterDialog(ChapterItem chapter) {
        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(getContext());

        if (dialogPresenter.isDialogShown() && dialogPresenter.getId() != CHAPTER_NOTIFICATION_Id) {
            // Another dialog is opened. Don't distract a user.
            return;
        }

        if (dialogPresenter.isDialogShown() && getPlayer() != null && !getPlayer().isPlaying()) {
            return;
        }

        dialogPresenter.closeDialog(); // remove previous dialog

        if (chapter == null || getPlayer() == null || getPlayer().isOverlayShown() || getPlayer().isInPIPMode() ||
                Utils.isScreenOff(getContext())) {
            return;
        }

        OptionItem acceptOption = UiOptionItem.from(
                chapter.getTitle(),
                option -> {
                    // return to previous dialog or close if no other dialogs in stack
                    dialogPresenter.closeDialog();
                    ChapterItem nextChapter = getNextChapter();
                    getPlayer().setPositionMs(nextChapter != null ? nextChapter.getStartTimeMs() : getPlayer().getDurationMs());
                }
        );

        dialogPresenter.appendSingleButton(acceptOption);

        dialogPresenter.enableTransparent(true);
        dialogPresenter.enableExpandable(false);
        dialogPresenter.setId(CHAPTER_NOTIFICATION_Id);
        dialogPresenter.showDialog();
    }

    private ChapterItem getNextChapter() {
        if (mChapters == null) {
            return null;
        }

        long positionMs = getPlayer().getPositionMs();
        for (ChapterItem chapter : mChapters) {
            if (chapter.getStartTimeMs() > (positionMs + 3_000)) {
                return chapter;
            }
        }

        return null;
    }

    private ChapterItem getCurrentChapter() {
        if (mChapters == null) {
            return null;
        }

        long positionMs = getPlayer().getPositionMs();
        ChapterItem currentChapter = null;

        for (ChapterItem chapter : mChapters) {
            if (chapter.getStartTimeMs() > (positionMs + 3_000)) {
                break;
            }
            currentChapter = chapter;
        }

        return currentChapter;
    }

    private void callListener(MediaItemMetadata mediaItemMetadata) {
        if (mediaItemMetadata != null) {
            getMainController().onMetadata(mediaItemMetadata);
        }
    }

    private void disposeActions() {
        RxHelper.disposeActions(mActions);
        mChapters = null;
    }

    private boolean isSectionPlaylistEnabled(Video video) {
        return mPlayerTweaksData.isSectionPlaylistEnabled() && video != null && video.getGroup() != null &&
                (video.playlistId == null || video.nextMediaItem == null || video.belongsToSearch()) && (!video.isRemote || video.remotePlaylistId == null);
    }

    private void appendDislikes(Video video) {
        if (video == null) {
            return;
        }

        if (!mPlayerTweaksData.isLikesCounterEnabled()) {
            video.likeCount = null;
            video.dislikeCount = null;
            getPlayer().setVideo(video);
            return;
        }

        Observable<DislikeData> dislikeDataObserve = mMediaItemService.getDislikeDataObserve(video.videoId);

        Disposable dislikeAction = dislikeDataObserve.subscribe(
                dislikeData -> {
                    video.sync(dislikeData);
                    getPlayer().setVideo(video);
                },
                error -> Log.e(TAG, "Dislike not working...")
        );

        mActions.add(dislikeAction);
    }
}
