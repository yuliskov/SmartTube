package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaGroupManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.locale.LocaleUtility;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.Presenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelSubView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class ChannelSubPresenter implements VideoGroupPresenter, Presenter<ChannelSubView> {
    private static final String TAG = ChannelSubPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static ChannelSubPresenter sInstance;
    private final Context mContext;
    private final PlaybackPresenter mPlaybackPresenter;
    private final MediaGroupManager mGroupManager;
    private ChannelSubView mView;
    private Disposable mUpdateAction;
    private Disposable mScrollAction;
    private Video mVideoItem;

    public ChannelSubPresenter(Context context) {
        mContext = context;
        MediaService mediaService = YouTubeMediaService.instance(LocaleUtility.getCurrentLocale(context));
        mGroupManager = mediaService.getMediaGroupManager();
        mPlaybackPresenter = PlaybackPresenter.instance(context);
    }

    public static ChannelSubPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new ChannelSubPresenter(context.getApplicationContext());
        }

        return sInstance;
    }

    @Override
    public void onInitDone() {
        if (mVideoItem != null) {
            mView.clear();
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
        VideoMenuPresenter.instance(mContext).showMenu(item);
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
    public void register(ChannelSubView view) {
        mView = view;
    }

    @Override
    public void unregister(ChannelSubView view) {
        mView = null;

        disposeActions();
    }

    public void openChannel(Video item) {
        if (item == null || !item.isChannelSub()) {
            return;
        }

        disposeActions();
        ViewManager.instance(mContext).startView(ChannelSubView.class);

        if (mView != null) {
            mVideoItem = null;
            mView.clear();
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

        mView.showProgressBar(true);

        MediaGroup mediaGroup = group.getMediaGroup();

        mScrollAction = mGroupManager.continueGroupObserve(mediaGroup)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(continueMediaGroup -> {
                            mView.update(VideoGroup.from(continueMediaGroup));
                        }, error -> Log.e(TAG, "continueGroup error: " + error),
                        () -> mView.showProgressBar(false));
    }

    private void updateVideoGrid(Observable<MediaGroup> group) {
        Log.d(TAG, "updateVideoGrid: Start loading group...");

        mView.showProgressBar(true);

        mUpdateAction = group
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        mediaGroup -> {
                            mView.update(VideoGroup.from(mediaGroup));

                            // Hide loading as long as first group received
                            if (mediaGroup.getMediaItems() != null) {
                                mView.showProgressBar(false);
                            }
                        }
                        , error -> Log.e(TAG, "updateGridHeader error: " + error)
                        , () -> {
                            mView.showProgressBar(false);
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
