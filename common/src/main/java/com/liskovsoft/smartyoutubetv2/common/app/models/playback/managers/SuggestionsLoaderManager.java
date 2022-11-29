package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import androidx.core.content.ContextCompat;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.ChapterItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.ContentBlockManager.SeekBarSegment;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SuggestionsLoaderManager extends PlayerEventListenerHelper {
    private static final String TAG = SuggestionsLoaderManager.class.getSimpleName();
    private final Set<MetadataListener> mListeners = new HashSet<>();
    private final List<Disposable> mActions = new ArrayList<>();
    private PlayerTweaksData mPlayerTweaksData;
    private VideoGroup mLastScrollGroup;

    public interface MetadataListener {
        void onMetadata(MediaItemMetadata metadata);
    }

    @Override
    public void onInitDone() {
        mPlayerTweaksData = PlayerTweaksData.instance(getActivity());
    }

    @Override
    public void openVideo(Video item) {
        // Remote control fix. Slow network fix. Suggestions may still be loading.
        // This could lead to changing current video info (title, id etc) to wrong one.
        disposeActions();
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

        VideoGroup group = item.group;

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

        // Update UI to response to user clicks
        getController().resetSuggestedPosition();
    }

    @Override
    public void onControlsShown(boolean shown) {
        if (shown) {
            focusCurrentChapter();
        }
    }

    @Override
    public void onSeekEnd() {
        if (getController().isControlsShown()) {
            focusCurrentChapter();
        }
    }

    private void continueGroup(VideoGroup group) {
        Log.d(TAG, "continueGroup: start continue group: " + group.getTitle());

        getController().showProgressBar(true);

        MediaGroup mediaGroup = group.getMediaGroup();

        MediaItemService mediaItemManager = YouTubeMediaService.instance().getMediaItemService();

        Disposable continueAction = mediaItemManager.continueGroupObserve(mediaGroup)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        continueMediaGroup -> {
                            getController().showProgressBar(false);
                            VideoGroup videoGroup = VideoGroup.from(continueMediaGroup, group.getSection());
                            getController().updateSuggestions(videoGroup);

                            // Merge remote queue with player's queue
                            Video video = getController().getVideo();
                            if (video != null && video.isRemote && getController().getSuggestionsIndex(videoGroup) == 0) {
                                Playlist.instance().addAll(videoGroup.getVideos());
                                Playlist.instance().setCurrent(video);
                            }

                            continueGroupIfNeeded(videoGroup);
                        },
                        error -> {
                            Log.e(TAG, "continueGroup error: %s", error.getMessage());
                            if (getController() != null) {
                                getController().showProgressBar(false);
                            }
                        },
                        () -> {
                            if (getController() != null) {
                                getController().showProgressBar(false);
                            }
                        }
                );

        mActions.add(continueAction);
    }

    private void syncCurrentVideo(MediaItemMetadata mediaItemMetadata, Video video) {
        if (getController().containsMedia()) {
            video.isUpcoming = false; // live stream started
        }
        video.sync(mediaItemMetadata, PlayerData.instance(getActivity()).isAbsoluteDateEnabled());
        getController().setVideo(video);

        getController().setNextTitle(getNextTitle());
    }

    private String getNextTitle() {
        String title = null;

        Video nextVideo = Playlist.instance().getNext();
        Video video = getController().getVideo();

        if (nextVideo != null) {
            title = nextVideo.title;
        } else if (video != null && video.nextMediaItem != null) {
            title = video.nextMediaItem.getTitle();
        }

        return title;
    }

    public void loadSuggestions(Video video) {
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

        clearSuggestionsIfNeeded(video);

        Disposable metadataAction = observable
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        metadata -> updateSuggestions(metadata, video),
                        error -> {
                            Log.e(TAG, "loadSuggestions error: %s", error.getMessage());
                            error.printStackTrace();
                            // Errors are usual here (something with title parsing)
                        }
                );

        mActions.add(metadataAction);
    }

    private void clearSuggestionsIfNeeded(Video video) {
        if (video == null || getController() == null) {
            return;
        }

        // Frees a lot of memory
        if (video.isRemote || !getController().isSuggestionsShown()) {
            getController().clearSuggestions();
        }
    }

    private void updateSuggestions(MediaItemMetadata mediaItemMetadata, Video video) {
        syncCurrentVideo(mediaItemMetadata, video);

        addChapterMarkersIfNeeded(mediaItemMetadata);

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

        if (!video.isRemote && getController().isSuggestionsShown()) {
            Log.d(TAG, "Suggestions is opened. Seems that user want to stay here.");
            return;
        }

        getController().clearSuggestions(); // clear previous videos

        appendChaptersIfNeeded(mediaItemMetadata);

        appendUserQueueIfNeeded(video);

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

                getController().updateSuggestions(videoGroup);

                continueGroupIfNeeded(videoGroup);
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

    private void addChapterMarkersIfNeeded(MediaItemMetadata mediaItemMetadata) {
        List<ChapterItem> chapters = mediaItemMetadata.getChapters();

        if (chapters == null) {
            return;
        }

        getController().setSeekBarSegments(toSeekBarSegments(chapters));
    }

    private void appendChaptersIfNeeded(MediaItemMetadata mediaItemMetadata) {
        List<ChapterItem> chapters = mediaItemMetadata.getChapters();

        if (chapters == null) {
            return;
        }

        VideoGroup videoGroup = VideoGroup.fromChapters(chapters, getActivity().getString(R.string.chapters));

        getController().updateSuggestions(videoGroup);
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
        for (Video item : queue) {
            item.group = videoGroup;
        }
        getController().updateSuggestions(videoGroup);
    }

    private void markAsQueueIfNeeded(Video item) {
        List<Video> afterCurrent = Playlist.instance().getAllAfterCurrent();

        if (afterCurrent != null && afterCurrent.contains(item)) {
            item.fromQueue = true;
        }
    }

    private void focusCurrentChapter() {
        VideoGroup group = getController().getSuggestionsByIndex(0);

        if (group == null || group.getVideos() == null) {
            return;
        }

        int index = findCurrentChapterIndex(group.getVideos());

        if (index != -1) {
            getController().focusSuggestedItem(index);
            getController().setTitle(group.getVideos().get(index).title);
        }
    }

    private int findCurrentChapterIndex(List<Video> videos) {
        if (videos == null || !videos.get(0).isChapter) {
            return -1;
        }

        int currentChapter = -1;
        long positionMs = getController().getPositionMs();
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
        long markLengthMs = getController().getDurationMs() / 100;

        for (ChapterItem chapter : chapters) {
            if (chapter.getStartTimeMs() == 0) {
                continue;
            }

            SeekBarSegment seekBarSegment = new SeekBarSegment();
            double startRatio = (double) chapter.getStartTimeMs() / getController().getDurationMs(); // Range: [0, 1]
            double endRatio = (double) (chapter.getStartTimeMs() + markLengthMs) / getController().getDurationMs(); // Range: [0, 1]
            seekBarSegment.startProgress = (int) (startRatio * Integer.MAX_VALUE); // Could safely cast to int
            seekBarSegment.endProgress = (int) (endRatio * Integer.MAX_VALUE); // Could safely cast to int
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
        RxUtils.disposeActions(mActions);
        mLastScrollGroup = null;
    }
}
