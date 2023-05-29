package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import androidx.core.content.ContextCompat;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.ChapterItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEngineEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SuggestionsController extends PlayerEventListenerHelper {
    private static final String TAG = SuggestionsController.class.getSimpleName();
    private final Set<MetadataListener> mListeners = new HashSet<>();
    private final List<Disposable> mActions = new ArrayList<>();
    private PlayerTweaksData mPlayerTweaksData;
    private VideoGroup mLastScrollGroup;
    private VideoGroup mCurrentGroup; // disable garbage collected
    private VideoGroup mNextGroup; // disable garbage collected
    private Video mNextVideo;
    private int mFocusCount;
    private int mNextCount;
    private List<ChapterItem> mChapters;
    private final Runnable mChapterHandler = this::startChapterNotificationServiceIfNeededInt;
    private static final int CHAPTER_NOTIFICATION_Id = 565;

    public interface MetadataListener {
        void onMetadata(MediaItemMetadata metadata);
    }

    private interface OnVideoGroup {
        void onVideoGroup(VideoGroup group);
    }

    private interface OnMetadata {
        void onMetadata(MediaItemMetadata metadata);
    }

    @Override
    public void onInit() {
        mPlayerTweaksData = PlayerTweaksData.instance(getActivity());
    }

    @Override
    public void openVideo(Video item) {
        // Remote control fix. Slow network fix. Suggestions may still be loading.
        // This could lead to changing current video info (title, id etc) to wrong one.
        disposeActions();
        mCurrentGroup = item.getGroup(); // disable garbage collected
        mNextGroup = null; // enable garbage collected
        mNextVideo = null;
    }

    @Override
    public void onSourceChanged(Video item) {
        loadSuggestions(item);
    }

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

        if (mLastScrollGroup == group) {
            Log.d(TAG, "Can't continue group. Another action is running.");
            return;
        }

        mLastScrollGroup = group;

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
        Video video = getPlayer().getVideo();

        if (video == null || !video.isLive || RxHelper.isAnyActionRunning(mActions)) {
            return;
        }

        loadMetadata(video, metadata -> {
            syncCurrentVideo(metadata, video);
        });
    }

    private void continueGroup(VideoGroup group) {
        continueGroup(group, null);
    }

    private void continueGroup(VideoGroup group, OnVideoGroup callback) {
        if (group == null) {
            return;
        }

        Log.d(TAG, "continueGroup: start continue group: " + group.getTitle());

        getPlayer().showProgressBar(true);

        MediaGroup mediaGroup = group.getMediaGroup();

        MediaItemService mediaItemManager = YouTubeMediaService.instance().getMediaItemService();

        Disposable continueAction = mediaItemManager.continueGroupObserve(mediaGroup)
                .subscribe(
                        continueMediaGroup -> {
                            getPlayer().showProgressBar(false);
                            VideoGroup videoGroup = VideoGroup.from(continueMediaGroup, group.getSection());
                            getPlayer().updateSuggestions(videoGroup);

                            // Merge remote queue with player's queue
                            Video video = getPlayer().getVideo();
                            if (video != null && video.isRemote && getPlayer().getSuggestionsIndex(videoGroup) == 0) {
                                Playlist.instance().addAll(videoGroup.getVideos());
                                Playlist.instance().setCurrent(video);
                            }

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
                        },
                        () -> {
                            if (getPlayer() != null) {
                                getPlayer().showProgressBar(false);
                            }
                        }
                );

        mActions.add(continueAction);
    }

    private void syncCurrentVideo(MediaItemMetadata mediaItemMetadata, Video video) {
        if (getPlayer().containsMedia()) {
            video.isUpcoming = false; // live stream started
        }
        video.sync(mediaItemMetadata, PlayerData.instance(getActivity()).isAbsoluteDateEnabled());
        getPlayer().setVideo(video);

        getPlayer().setNextTitle(getNextTitle());
    }

    private String getNextTitle() {
        String title = null;

        Video nextVideo = Playlist.instance().getNext();
        Video video = getPlayer().getVideo();

        if (nextVideo != null) {
            title = nextVideo.title;
        } else if (video != null && video.nextMediaItem != null) {
            title = video.nextMediaItem.getTitle();
        }

        return title;
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

        MediaService service = YouTubeMediaService.instance();
        MediaItemService mediaItemManager = service.getMediaItemService();

        Observable<MediaItemMetadata> observable;

        // NOTE: Load suggestions from mediaItem isn't robust. Because playlistId may be initialized from RemoteControlManager.
        // Video might be loaded from Channels section (has playlistParams)
        observable = mediaItemManager.getMetadataObserve(video.videoId, video.getPlaylistId(), video.playlistIndex, video.playlistParams);

        Disposable metadataAction = observable
                .subscribe(
                        callback::onMetadata,
                        error -> {
                            MessageHelpers.showLongMessage(getActivity(), "loadSuggestions error: %s", error.getMessage());
                            Log.e(TAG, "loadSuggestions error: %s", error.getMessage());
                            error.printStackTrace();
                            // Errors are usual here (something with title parsing)
                        }
                );

        mActions.add(metadataAction);
    }

    public Video getNext() {
        return mNextVideo;
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
        List<MediaGroup> suggestions = mediaItemMetadata.getSuggestions();

        if (suggestions == null) {
            String msg = "loadSuggestions: Can't obtain suggestions for video: " + video.title;
            Log.e(TAG, msg);
            return;
        }

        if (mPlayerTweaksData.isSuggestionsDisabled()) {
            Log.d(TAG, "loadSuggestions: suggestions disabled by the user");
            return;
        }

        if (!video.isRemote && getPlayer().isSuggestionsShown()) {
            Log.d(TAG, "Suggestions is opened. Seems that user want to stay here.");
            return;
        }

        getPlayer().clearSuggestions(); // clear previous videos

        appendChaptersIfNeeded(mediaItemMetadata);

        appendUserQueueIfNeeded(video);

        appendSectionPlaylistIfNeeded(video);

        int groupIndex = -1;
        int suggestRows = -1;

        if (GeneralData.instance(getActivity()).isChildModeEnabled()) {
            suggestRows = video.hasPlaylist() ? 1 : 0;
        }

        for (MediaGroup group : suggestions) {
            groupIndex++;

            if (groupIndex == suggestRows) {
                break;
            }

            if (group != null && !group.isEmpty()) {
                VideoGroup videoGroup = VideoGroup.from(group);

                if (groupIndex == 0) {
                    mergeRemoteAndUserQueueIfNeeded(video, videoGroup);
                }

                getPlayer().updateSuggestions(videoGroup);

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
    private void mergeRemoteAndUserQueueIfNeeded(Video video, VideoGroup videoGroup) {
        // NOTE: Commented out section below has risk of adding random videos into the queue
        //if (video.isRemote && (video.remotePlaylistId != null || !Playlist.instance().hasNext())) {
        if (video.isRemote && video.remotePlaylistId != null) {
            videoGroup.removeAllBefore(video);
            // Double queue bugfix. Remove remote playlist id from the videos.
            videoGroup.stripPlaylistInfo();

            videoGroup.setTitle(getActivity().getString(R.string.action_playback_queue));
            videoGroup.setId(videoGroup.getTitle().hashCode());

            Playlist.instance().removeAllAfterCurrent();
            Playlist.instance().addAll(videoGroup.getVideos());
            Playlist.instance().setCurrent(video);
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

        VideoGroup videoGroup = VideoGroup.fromChapters(mChapters, getActivity().getString(R.string.chapters));

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

        if (mChapters != null) {
            getPlayer().setSeekPreviewTitle("..."); // fix control panel animation on the first run
        }
    }

    private void appendUserQueueIfNeeded(Video video) {
        // Exclude situations when phone cast just started or next item is null
        if ((video.isRemote && video.remotePlaylistId != null) || !Playlist.instance().hasNext()) {
            return;
        }

        List<Video> queue = Playlist.instance().getAllAfterCurrent();

        VideoGroup videoGroup = VideoGroup.from(queue);
        videoGroup.setTitle(getActivity().getString(R.string.action_playback_queue));
        videoGroup.setId(videoGroup.getTitle().hashCode());
        getPlayer().updateSuggestions(videoGroup);
    }

    private void appendSectionPlaylistIfNeeded(Video video) {
        if (video.getGroup() == null || video.playlistId != null || video.remotePlaylistId != null || !mPlayerTweaksData.isSectionPlaylistEnabled()) {
            return;
        }

        getPlayer().updateSuggestions(video.getGroup());
        focusAndContinueIfNeeded(video.getGroup());
        appendNextVideoIfNeeded(video.getGroup());
    }

    private void markAsQueueIfNeeded(Video item) {
        List<Video> afterCurrent = Playlist.instance().getAllAfterCurrent();

        if (afterCurrent != null && afterCurrent.contains(item)) {
            item.fromQueue = true;
        }
    }

    private void focusCurrentChapter() {
        VideoGroup group = getPlayer().getSuggestionsByIndex(0);

        if (group == null || group.getVideos() == null) {
            return;
        }

        int index = findCurrentChapterIndex(group.getVideos());

        if (index != -1) {
            String title = group.getVideos().get(index).title;
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
            seekBarSegment.color = ContextCompat.getColor(getActivity(), R.color.black);
            result.add(seekBarSegment);
        }

        return result;
    }

    /**
     * Most tiny ui has 8 cards in a row or 24 in grid.
     */
    private void continueGroupIfNeeded(VideoGroup group) {
        MediaServiceManager.instance().shouldContinueTheGroup(getActivity(), group, () -> continueGroup(group));
    }

    public void focusAndContinueIfNeeded(VideoGroup group) {
        Video video = getPlayer().getVideo();

        if (group == null || group.isEmpty() || video == null || !video.hasVideo()) {
            return;
        }

        int index = group.getVideos().indexOf(video);

        if (index >= 0) { // continuation group starts with zero index
            Log.d(TAG, "Found current video index: %s", index);
            Video found = group.getVideos().get(index);
            if (!found.isMix()) {
                getPlayer().focusSuggestedItem(found);
            }
            mFocusCount = 0; // Stop the continuation loop
        } else if (mFocusCount > 5 || !video.hasPlaylist()) {
            // Stop the continuation loop. Maybe the video isn't there.
            mFocusCount = 0;
        } else {
            // load more and repeat
            continueGroup(group, this::focusAndContinueIfNeeded);
            mFocusCount++;
        }
    }

    private void appendNextVideoIfNeeded(VideoGroup group) {
        Video video = getPlayer().getVideo();

        if (group == null || group.isEmpty() || video == null || !video.hasVideo()) {
            return;
        }

        List<Video> videos = group.getVideos();
        boolean found = false;

        for (Video current : videos) {
            if ((found || mNextCount > 0) && current.hasVideo() && !current.isUpcoming) {
                getPlayer().setNextTitle(current.title);
                mNextCount = 0;
                mNextGroup = group;
                mNextVideo = current;
                return;
            }

            if (current.equals(video)) {
                found = true;
            }
        }

        if (mNextCount > 0) {
            mNextCount = 0;
        } else {
            continueGroup(group, this::appendNextVideoIfNeeded);
            mNextCount++;
        }
    }

    private void showChapterDialog(ChapterItem chapter) {
        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(getActivity());

        if ((dialogPresenter.isDialogShown() && dialogPresenter.getId() != CHAPTER_NOTIFICATION_Id) ||
                getPlayer() == null || getPlayer().isOverlayShown() || getPlayer().isInPIPMode()) {
            // Another dialog is opened. Don't distract a user.
            return;
        }

        dialogPresenter.closeDialog(); // remove previous dialog

        if (chapter == null) {
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

    public void addMetadataListener(MetadataListener listener) {
        mListeners.add(listener);
    }

    private void callListener(MediaItemMetadata mediaItemMetadata) {
        if (mediaItemMetadata != null) {
            for (MetadataListener listener : mListeners) {
                listener.onMetadata(mediaItemMetadata);
            }
        }
    }

    private void disposeActions() {
        RxHelper.disposeActions(mActions);
        mLastScrollGroup = null;
        mChapters = null;
    }
}
