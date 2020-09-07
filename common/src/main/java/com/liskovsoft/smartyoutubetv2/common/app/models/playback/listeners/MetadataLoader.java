package com.liskovsoft.smartyoutubetv2.common.app.models.playback.listeners;

import android.annotation.SuppressLint;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
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

    @SuppressLint("CheckResult")
    private void loadSuggestions(Video video) {
        if (video == null) {
            Log.e(TAG, "loadSuggestions: video is null");
            return;
        }

        mController.clearSuggestions(); // clear previous videos

        MediaService service = YouTubeMediaService.instance();
        MediaItemManager mediaItemManager = service.getMediaItemManager();
        mediaItemManager.getMetadataObserve(video.videoId)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(mediaItemMetadata -> {
                    if (mediaItemMetadata == null) {
                        Log.e(TAG, "loadSuggestions: Item doesn't contain metadata: " + video.title);
                        return;
                    }

                    mStateUpdater.onMetadataLoaded(mediaItemMetadata);

                    List<MediaGroup> suggestions = mediaItemMetadata.getSuggestions();

                    if (suggestions == null) {
                        Log.e(TAG, "loadSuggestions: Can't obtain suggestions for video : " + video.title);
                        return;
                    }

                    for (MediaGroup group : suggestions) {
                        mController.updateSuggestions(VideoGroup.from(group, null));
                    }
                }, error -> Log.e(TAG, "loadSuggestions: " + error));
    }
}
