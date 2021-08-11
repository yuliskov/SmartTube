package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaGroupManager;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.VideoMenuPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.utils.RxUtils;
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
    private final MediaItemManager mItemManager;
    private Disposable mUpdateAction;
    private Disposable mScrollAction;
    private Video mVideoItem;

    public ChannelUploadsPresenter(Context context) {
        super(context);
        MediaService mediaService = YouTubeMediaService.instance();
        mGroupManager = mediaService.getMediaGroupManager();
        mItemManager = mediaService.getMediaItemManager();
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
    public void onVideoItemSelected(Video item) {
        // NOP
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
        if (item.isVideo()) {
            VideoMenuPresenter.instance(getContext()).showVideoMenu(item);
        } else if (item.isChannel()) {
            VideoMenuPresenter.instance(getContext()).showChannelMenu(item);
        }
    }

    @Override
    public void onScrollEnd(Video item) {
        if (item == null) {
            Log.e(TAG, "Can't scroll. Video is null.");
            return;
        }

        VideoGroup group = item.group;

        Log.d(TAG, "onScrollEnd: Group title: " + group.getTitle());

        boolean scrollInProgress = mScrollAction != null && !mScrollAction.isDisposed();

        if (!scrollInProgress) {
            continueVideoGroup(group);
        }
    }

    @Override
    public void onViewDestroyed() {
        super.onViewDestroyed();
        disposeActions();
    }

    @Override
    public boolean hasPendingActions() {
        return RxUtils.isAnyActionRunning(mScrollAction, mUpdateAction);
    }

    public void openChannel(Video item) {
        // Working with uploads or playlists
        if (item == null || (!item.hasUploads() && item.playlistId == null)) {
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
        if (item != null && item.mediaItem != null) {
            updateVideoGrid(item.mediaItem, callback);
        }
    }

    public Observable<MediaGroup> obtainVideoGroupObservable(Video item) {
        if (item == null) {
            return null;
        }

        return item.hasUploads() ?
                mGroupManager.getGroupObserve(item.mediaItem) :
                mItemManager.getMetadataObserve(item.videoId, item.playlistId, 0)
                        .flatMap(mediaItemMetadata -> Observable.just(mediaItemMetadata.getSuggestions().get(0)));
    }

    private void updateGrid(Video item) {
        updateVideoGrid(obtainVideoGroupObservable(item));
    }

    private void disposeActions() {
        RxUtils.disposeActions(mUpdateAction, mScrollAction);
    }

    private void continueVideoGroup(VideoGroup group) {
        Log.d(TAG, "continueGroup: start continue group: " + group.getTitle());

        getView().showProgressBar(true);

        MediaGroup mediaGroup = group.getMediaGroup();

        Observable<MediaGroup> continuation;

        if (mediaGroup.getType() == MediaGroup.TYPE_SUGGESTIONS) {
            continuation = mItemManager.continueGroupObserve(mediaGroup);
        } else {
            continuation = mGroupManager.continueGroupObserve(mediaGroup);
        }

        mScrollAction = continuation
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        continueMediaGroup -> getView().update(VideoGroup.from(continueMediaGroup)),
                        error -> {
                            Log.e(TAG, "continueGroup error: %s", error.getMessage());
                            getView().showProgressBar(false);
                        },
                        () -> getView().showProgressBar(false)
                );
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
                        },
                        error -> Log.e(TAG, "updateGridHeader error: %s", error.getMessage()),
                        () -> getView().showProgressBar(false)
                );
    }

    private void updateVideoGrid(MediaItem mediaItem, VideoGroupCallback callback) {
        Log.d(TAG, "updateVideoGrid: Start loading group...");

        Observable<MediaGroup> group = mGroupManager.getGroupObserve(mediaItem);

        mUpdateAction = group
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        callback::onGroup,
                        error -> Log.e(TAG, "updateVideoGrid error: %s", error.getMessage())
                );
    }

    public interface VideoGroupCallback {
        void onGroup(MediaGroup mediaGroup);
    }
}
