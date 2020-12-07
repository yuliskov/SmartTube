package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaGroupManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class ChannelUploadsPresenter extends BasePresenter<ChannelUploadsView> implements VideoGroupPresenter {
    private static final String TAG = ChannelUploadsPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static ChannelUploadsPresenter sInstance;
    private final PlaybackPresenter mPlaybackPresenter;
    private final MediaGroupManager mGroupManager;
    private Disposable mUpdateAction;
    private Disposable mScrollAction;
    private Video mVideoItem;

    public ChannelUploadsPresenter(Context context) {
        super(context);
        MediaService mediaService = YouTubeMediaService.instance();
        mGroupManager = mediaService.getMediaGroupManager();
        mPlaybackPresenter = PlaybackPresenter.instance(context);
    }

    public static ChannelUploadsPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new ChannelUploadsPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    @Override
    public void onViewInitialized() {
        if (mVideoItem != null) {
            getView().clear();
            updateGrid(mVideoItem);
        }
    }

    @Override
    public void onVideoItemClicked(Video item) {
        if (item.isVideo()) {
            mPlaybackPresenter.openVideo(item);
        } else if (item.isChannel()) {
            openChannel(item);
        }
    }

    @Override
    public void onVideoItemLongClicked(Video item) {
        VideoMenuPresenter.instance(getContext()).showMenu(item);
    }

    @Override
    public void onScrollEnd(VideoGroup group) {
        Log.d(TAG, "onScrollEnd: Group title: " + group.getTitle());

        boolean scrollInProgress = mScrollAction != null && !mScrollAction.isDisposed();

        if (!scrollInProgress) {
            continueVideoGroup(group);
        }
    }

    @Override
    public void onViewDestroyed() {
        disposeActions();
    }

    public void openChannel(Video item) {
        if (item == null) {
            return;
        }

        disposeActions();
        ViewManager.instance(getContext()).startView(ChannelUploadsView.class);

        if (getView() != null) {
            mVideoItem = null;
            getView().clear();
            updateGrid(item);
        } else {
            mVideoItem = item;
        }
    }

    public void obtainVideoGroup(Video item, VideoGroupCallback callback) {
        if (item != null) {
            updateVideoGrid(item.mediaItem, callback);
        }
    }

    private void updateGrid(Video item) {
        updateVideoGrid(mGroupManager.getGroupObserve(item.mediaItem));
    }

    private void disposeActions() {
        if (mUpdateAction != null && !mUpdateAction.isDisposed()) {
            mUpdateAction.dispose();
        }

        if (mScrollAction != null && !mScrollAction.isDisposed()) {
            mScrollAction.dispose();
        }
    }

    private void continueVideoGroup(VideoGroup group) {
        Log.d(TAG, "continueGroup: start continue group: " + group.getTitle());

        getView().showProgressBar(true);

        MediaGroup mediaGroup = group.getMediaGroup();

        mScrollAction = mGroupManager.continueGroupObserve(mediaGroup)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(continueMediaGroup -> {
                            getView().update(VideoGroup.from(continueMediaGroup));
                        }, error -> Log.e(TAG, "continueGroup error: " + error),
                        () -> getView().showProgressBar(false));
    }

    private void updateVideoGrid(Observable<MediaGroup> group) {
        Log.d(TAG, "updateVideoGrid: Start loading group...");

        getView().showProgressBar(true);

        mUpdateAction = group
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        mediaGroup -> {
                            getView().update(VideoGroup.from(mediaGroup));

                            // Hide loading as long as first group received
                            if (mediaGroup.getMediaItems() != null) {
                                getView().showProgressBar(false);
                            }
                        }
                        , error -> Log.e(TAG, "updateGridHeader error: " + error)
                        , () -> {
                            getView().showProgressBar(false);
                        });
    }

    private void updateVideoGrid(MediaItem mediaItem, VideoGroupCallback callback) {
        Log.d(TAG, "updateVideoGrid: Start loading group...");

        Observable<MediaGroup> group = mGroupManager.getGroupObserve(mediaItem);

        mUpdateAction = group
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(callback::onGroup, error -> Log.e(TAG, "updateVideoGrid error: " + error));
    }

    public interface VideoGroupCallback {
        void onGroup(MediaGroup mediaGroup);
    }
}
