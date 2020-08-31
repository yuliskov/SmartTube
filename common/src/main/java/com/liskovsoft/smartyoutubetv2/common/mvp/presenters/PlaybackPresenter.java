package com.liskovsoft.smartyoutubetv2.common.mvp.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.mvp.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.Playlist;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.Video;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.mvp.views.PlaybackView;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

import java.io.InputStream;
import java.util.List;

public class PlaybackPresenter implements VideoGroupPresenter<PlaybackView> {
    private static final String TAG = PlaybackPresenter.class.getSimpleName();
    private static PlaybackPresenter sInstance;
    private final Context mContext;
    private final Playlist mPlaylist;
    private final ViewManager mViewManager;
    private PlaybackView mView;
    private Video mVideo;

    private PlaybackPresenter(Context context) {
        mContext = context;
        mPlaylist = new Playlist();
        mViewManager = ViewManager.instance(context);
    }

    public static PlaybackPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new PlaybackPresenter(context.getApplicationContext());
        }

        return sInstance;
    }

    @Override
    public void onInitDone() {
        if (mView != null) {
            loadVideo(mVideo);
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
    private void loadSuggestedVideos(Video video) {
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

                    if (suggestions == null) {
                        Log.e(TAG, "Can't obtain suggestions for video : " + video.title);
                        return;
                    }

                    for (MediaGroup group : suggestions) {
                        mView.updateRelatedVideos(VideoGroup.from(group, null));
                    }
                }, error -> Log.e(TAG, "loadSuggestedVideos: " + error));
    }

    @SuppressLint("CheckResult")
    private void loadFormatInfo(Video video) {
        MediaService service = YouTubeMediaService.instance();
        MediaItemManager mediaItemManager = service.getMediaItemManager();
        mediaItemManager.getFormatInfoObserve(video.videoId)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(formatInfo -> {
                    if (formatInfo == null) {
                        Log.e(TAG, "Can't obtains format info: " + video.title);
                        return;
                    }

                    InputStream mpdStream = formatInfo.getMpdStream();

                    mView.loadDashStream(mpdStream);
                }, error -> Log.e(TAG, "loadFormatInfo: " + error));
    }

    @Override
    public void onVideoItemClicked(Video video) {
        if (mView != null && video != null) {
            appendToPlaylist(video);
            loadVideo(video);
        }
    }

    @Override
    public void onVideoItemLongClicked(Video item) {
        // TODO: not implemented
    }

    private void loadVideo(Video video) {
        if (mView != null && video != null) {
            mVideo = video;
            mView.openVideo(video);
            loadFormatInfo(video);
            loadSuggestedVideos(video);
        }
    }

    public void onPrevious() {
        loadVideo(mPlaylist.previous());
    }

    public void onNext() {
        loadVideo(mPlaylist.next());
    }

    public void openVideo(Video video) {
        mVideo = video;

        appendToPlaylist(video);

        mViewManager.startView(PlaybackView.class);
    }

    private void appendToPlaylist(Video video) {
        if (mVideo != null) {
            mPlaylist.insert(mPlaylist.indexOf(mVideo) + 1, video);
        } else {
            mPlaylist.insert(mPlaylist.size() - 1, video);
        }

        //mPlaylist.add(video);
        //mPlaylist.setCurrentPosition(mPlaylist.size() - 1);
    }

    @Override
    public void onScrollEnd(VideoGroup group) {
        // NOP
    }
}
