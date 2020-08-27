package com.liskovsoft.smartyoutubetv2.common.mvp.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.Video;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.mvp.views.PlaybackView;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.schedulers.Schedulers;

import java.io.InputStream;
import java.util.List;

public class PlaybackPresenter implements Presenter<PlaybackView> {
    private static final String TAG = PlaybackPresenter.class.getSimpleName();
    private static PlaybackPresenter sInstance;
    private final Context mContext;
    private PlaybackView mView;

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
        if (mView != null) {
            loadFormatInfo();
            loadRelatedVideos();
        }
    }

    @Override
    public void register(PlaybackView view) {
        mView = view;
    }

    @Override
    public void unregister(PlaybackView view) {
        mView = null;
    }

    @SuppressLint("CheckResult")
    private void loadRelatedVideos() {
        Video video = mView.getVideo();

        MediaService service = YouTubeMediaService.instance();
        MediaItemManager mediaItemManager = service.getMediaItemManager();
        mediaItemManager.getMetadataObserve(video.videoId)
                .subscribeOn(Schedulers.newThread())
                .subscribe(mediaItemMetadata -> {
                    if (mediaItemMetadata == null) {
                        Log.e(TAG, "Item doesn't contain metadata: " + video.title);
                        return;
                    }

                    List<MediaGroup> suggestions = mediaItemMetadata.getSuggestions();

                    for (MediaGroup group : suggestions) {
                        mView.updateRelatedVideos(VideoGroup.from(group));
                    }
                }, error -> Log.e(TAG, error));
    }

    @SuppressLint("CheckResult")
    private void loadFormatInfo() {
        Video video = mView.getVideo();

        MediaService service = YouTubeMediaService.instance();
        MediaItemManager mediaItemManager = service.getMediaItemManager();
        mediaItemManager.getFormatInfoObserve(video.videoId)
                .subscribeOn(Schedulers.newThread())
                .subscribe(formatInfo -> {
                    if (formatInfo == null) {
                        Log.e(TAG, "Can't obtains format info: " + video.title);
                        return;
                    }

                    InputStream mpdStream = formatInfo.getMpdStream();

                    mView.loadDashStream(mpdStream);
                }, error -> Log.e(TAG, error));
    }

    public void onSuggestionItemClicked(Video video) {
        if (mView != null) {
            mView.openVideo(video);
        }
    }
}
