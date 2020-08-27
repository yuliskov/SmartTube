package com.liskovsoft.smartyoutubetv2.common.mvp.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemSuggestions;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.mvp.views.PlaybackView;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.schedulers.Schedulers;

import java.util.List;

public class PlaybackPresenter extends PresenterBase<PlaybackView> {
    private static final String TAG = PlaybackPresenter.class.getSimpleName();
    private static PlaybackPresenter sInstance;
    private final Context mContext;

    private PlaybackPresenter(Context context) {
        mContext = context;
    }

    public static PlaybackPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new PlaybackPresenter(context);
        }

        return sInstance;
    }

    @Override
    public void onInitDone() {
        loadRelatedVideos();
    }

    @SuppressLint("CheckResult")
    private void loadRelatedVideos() {
        MediaService service = YouTubeMediaService.instance();
        MediaItemManager mediaItemManager = service.getMediaItemManager();
        mediaItemManager.getMetadataObserve("3Gh5uKzmVaQ")
                .subscribeOn(Schedulers.newThread())
                .subscribe(mediaItemMetadata -> {
                    if (mediaItemMetadata == null) {
                        Log.e(TAG, "Item doesn't contain metadata");
                        return;
                    }

                    List<MediaItemSuggestions> suggestions = mediaItemMetadata.getSuggestions();

                    for (PlaybackView view : mViews) {
                        view.updateRelatedVideos(VideoGroup.from(suggestions.get(0))); // TODO: multiple rows
                    }
                }, error -> Log.e(TAG, error));
    }
}
