package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
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
    private List<Disposable> mActions = new ArrayList<>();
    private PlayerTweaksData mPlayerTweaksData;

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

        if (RxUtils.isAnyActionRunning(mActions)) {
            return;
        }

        continueGroup(item.group);
    }

    @Override
    public void onSuggestionItemClicked(Video item) {
        markAsQueueIfNeeded(item);

        // Update UI to response to user clicks
        getController().resetSuggestedPosition();
    }

    private void continueGroup(VideoGroup group) {
        Log.d(TAG, "continueGroup: start continue group: " + group.getTitle());

        getController().showProgressBar(true);

        MediaGroup mediaGroup = group.getMediaGroup();

        MediaItemManager mediaItemManager = YouTubeMediaService.instance().getMediaItemManager();

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
        // NOTE: Skip upcoming (no media) because default title more informative (e.g. has scheduled time).
        // NOTE: Upcoming videos metadata wrongly reported as live
        //if (getController().containsMedia() || video.getPlayerTitle() == null) {
        //    video.sync(mediaItemMetadata, PlayerData.instance(getActivity()).isAbsoluteDateEnabled());
        //    getController().setVideo(video);
        //}

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
        MediaItemManager mediaItemManager = service.getMediaItemManager();

        Observable<MediaItemMetadata> observable;

        // NOTE: Load suggestions from mediaItem isn't robust. Because playlistId may be initialized from RemoteControlManager
        //if (video.mediaItem != null && !video.isRemote) {
        //    observable = mediaItemManager.getMetadataObserve(video.mediaItem);
        //} else {
        //    // Video might be loaded from channels
        //    observable = mediaItemManager.getMetadataObserve(video.videoId, video.playlistId, video.playlistIndex);
        //}

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
                            // normally error could happen (something with title parsing)
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

        callListener(mediaItemMetadata);

        List<MediaGroup> suggestions = mediaItemMetadata.getSuggestions();

        if (suggestions == null) {
            String msg = "loadSuggestions: Can't obtain suggestions for video: " + video.title;
            Log.e(TAG, msg);
            //MessageHelpers.showMessage(getActivity(), msg);
            return;
        }

        if (mPlayerTweaksData.isSuggestionsDisabled()) {
            Log.d(TAG, "loadSuggestions: suggestions disabled by the user");
            return;
        }

        if (!video.isRemote) {
            //if (video.hasPlaylistIndex() && !getController().isSuggestionsEmpty()) {
            //    Log.d(TAG, "Skip reloading suggestions when watching playlist.");
            //    return;
            //}

            if (getController().isSuggestionsShown()) {
                Log.d(TAG, "Suggestions is opened. Seems that user want to stay here.");
                return;
            }
        }

        getController().clearSuggestions(); // clear previous videos

        appendUserQueueIfNeeded(video);

        int groupIndex = -1;

        for (MediaGroup group : suggestions) {
            groupIndex++;

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
    }
}
