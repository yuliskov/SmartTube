package com.liskovsoft.smartyoutubetv2.common.app.models.playback.processors;

import android.annotation.SuppressLint;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.schedulers.Schedulers;

import java.util.List;

public class SuggestionsLoader extends PlayerEventListenerHelper {
    private static final String TAG = SuggestionsLoader.class.getSimpleName();
    private final Playlist mPlaylist;
    private final PlayerController mController;

    public SuggestionsLoader(PlayerController controller) {
        mController = controller;
        mPlaylist = Playlist.instance();
    }

    @Override
    public void onInit(Video item) {
        loadSuggestions(item);
    }

    @Override
    public void onPrevious() {
        loadSuggestions(mPlaylist.previous());
    }

    @Override
    public void onNext() {
        loadSuggestions(mPlaylist.next());
    }

    @Override
    public void onSuggestionItemClicked(Video item) {
        loadSuggestions(item);
    }

    @SuppressLint("CheckResult")
    private void loadSuggestions(Video video) {
        if (video == null) {
            Log.e(TAG, "loadSuggestions: video is null");
            return;
        }

        mController.clearRelated(); // clear previous videos

        MediaService service = YouTubeMediaService.instance();
        MediaItemManager mediaItemManager = service.getMediaItemManager();
        mediaItemManager.getMetadataObserve(video.videoId)
                .subscribeOn(Schedulers.newThread())
                .subscribe(mediaItemMetadata -> {
                    if (mediaItemMetadata == null) {
                        Log.e(TAG, "loadSuggestions: Item doesn't contain metadata: " + video.title);
                        return;
                    }

                    List<MediaGroup> suggestions = mediaItemMetadata.getSuggestions();

                    if (suggestions == null) {
                        Log.e(TAG, "loadSuggestions: Can't obtain suggestions for video : " + video.title);
                        return;
                    }

                    for (MediaGroup group : suggestions) {
                        mController.updateRelated(VideoGroup.from(group, null));
                    }
                }, error -> Log.e(TAG, "loadSuggestions: " + error));
    }
}
