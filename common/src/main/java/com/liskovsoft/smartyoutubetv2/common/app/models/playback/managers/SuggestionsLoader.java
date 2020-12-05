package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.utils.RxUtils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.List;

public class SuggestionsLoader extends PlayerEventListenerHelper {
    private static final String TAG = SuggestionsLoader.class.getSimpleName();
    private final List<MetadataListener> mListeners = new ArrayList<>();
    private Disposable mMetadataAction;
    private Disposable mScrollAction;

    public interface MetadataListener {
        void onMetadata(MediaItemMetadata metadata);
    }

    @Override
    public void onSourceChanged(Video item) {
        loadSuggestions(item);
    }

    @Override
    public void onEngineReleased() {
        RxUtils.disposeActions(mMetadataAction, mScrollAction);
    }

    @Override
    public void onScrollEnd(VideoGroup group) {
        continueGroup(group);
    }

    @Override
    public void onSuggestionItemClicked(Video item) {
        // Visual response to user clicks
        getController().resetSuggestedPosition();
    }

    private void continueGroup(VideoGroup group) {
        boolean updateInProgress = mScrollAction != null && !mScrollAction.isDisposed();

        if (updateInProgress) {
            return;
        }

        Log.d(TAG, "continueGroup: start continue group: " + group.getTitle());

        MediaGroup mediaGroup = group.getMediaGroup();

        MediaItemManager mediaItemManager = YouTubeMediaService.instance().getMediaItemManager();

        mScrollAction = mediaItemManager.continueGroupObserve(mediaGroup)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        continueMediaGroup -> getController().updateSuggestions(VideoGroup.from(continueMediaGroup, group.getCategory()))
                        , error -> Log.e(TAG, "continueGroup error: " + error));
    }

    private void syncCurrentVideo(MediaItemMetadata mediaItemMetadata) {
        Video video = getController().getVideo();
        video.sync(mediaItemMetadata, PlayerData.instance(getActivity()).isShowFullDateEnabled());
        getController().setVideo(video);
    }

    private void loadSuggestions(Video video) {
        if (video == null) {
            Log.e(TAG, "loadSuggestions: video is null");
            return;
        }

        RxUtils.disposeActions(mMetadataAction, mScrollAction);

        MediaService service = YouTubeMediaService.instance();
        MediaItemManager mediaItemManager = service.getMediaItemManager();

        Observable<MediaItemMetadata> observable;

        if (video.mediaItem != null) {
            observable = mediaItemManager.getMetadataObserve(video.mediaItem);
        } else {
            // Video might be loaded from channels
            observable = mediaItemManager.getMetadataObserve(video.videoId);
        }

        mMetadataAction = observable
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::loadSuggestions,
                           error -> Log.e(TAG, "loadSuggestions error: " + error));
    }

    private void loadSuggestions(MediaItemMetadata mediaItemMetadata) {
        syncCurrentVideo(mediaItemMetadata);

        callListener(mediaItemMetadata);

        List<MediaGroup> suggestions = mediaItemMetadata.getSuggestions();

        if (suggestions == null) {
            Log.e(TAG, "loadSuggestions: Can't obtain suggestions for video: " + getController().getVideo().title);
            return;
        }

        // Don't reload suggestions when watching playlist items
        if (getController().getVideo().isPlaylistItem() && !getController().isSuggestionsEmpty()) {
            return;
        }

        getController().clearSuggestions(); // clear previous videos

        for (MediaGroup group : suggestions) {
            getController().updateSuggestions(VideoGroup.from(group));
        }
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
}
