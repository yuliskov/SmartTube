package com.liskovsoft.smartyoutubetv2.common.app.models.playback.listeners;

import android.annotation.SuppressLint;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

import java.util.List;

public class MetadataLoader extends PlayerEventListenerHelper {
    private static final String TAG = MetadataLoader.class.getSimpleName();
    private final StateUpdater mStateUpdater;

    public MetadataLoader(StateUpdater stateUpdater) {
        mStateUpdater = stateUpdater;
    }

    @Override
    public void onVideoLoaded(Video item) {
        loadSuggestions(item);
    }

    private void updateCurrentVideo(MediaItemMetadata mediaItemMetadata) {
        Video video = mController.getVideo();
        video.description = mediaItemMetadata.getDescription();
        video.mediaItemMetadata = mediaItemMetadata;
        mController.setVideo(video);
    }

    @SuppressLint("CheckResult")
    private void loadSuggestions(Video video) {
        if (video == null) {
            Log.e(TAG, "loadSuggestions: video is null");
            return;
        }

        String videoTitle = video.title;

        mController.clearSuggestions(); // clear previous videos

        if (video.mediaItemMetadata != null) {
            loadSuggestions(video.mediaItemMetadata, video.title);
            return;
        }

        MediaService service = YouTubeMediaService.instance();
        MediaItemManager mediaItemManager = service.getMediaItemManager();
        mediaItemManager.getMetadataObserve(video.videoId)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(mediaItemMetadata -> {
                    loadSuggestions(mediaItemMetadata, videoTitle);
                }, error -> Log.e(TAG, "loadSuggestions: " + error));
    }

    private void loadSuggestions(MediaItemMetadata mediaItemMetadata, String videoTitle) {
        if (mediaItemMetadata == null) {
            Log.e(TAG, "loadSuggestions: Video doesn't contain metadata: " + videoTitle);
            return;
        }

        //mStateUpdater.onMetadataLoaded(mediaItemMetadata);

        updateCurrentVideo(mediaItemMetadata);

        List<MediaGroup> suggestions = mediaItemMetadata.getSuggestions();

        if (suggestions == null) {
            Log.e(TAG, "loadSuggestions: Can't obtain suggestions for video: " + videoTitle);
            return;
        }

        for (MediaGroup group : suggestions) {
            mController.updateSuggestions(VideoGroup.from(group));
        }
    }
}
