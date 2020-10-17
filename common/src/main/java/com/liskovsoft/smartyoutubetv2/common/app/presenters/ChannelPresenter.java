package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaGroupManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.List;

public class ChannelPresenter implements VideoGroupPresenter<ChannelView> {
    private static final String TAG = ChannelPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static ChannelPresenter sInstance;
    private final Context mContext;
    private final MediaService mMediaService;
    private final PlaybackPresenter mPlaybackPresenter;
    private ChannelView mView;
    private String mChannelId;
    private Disposable mUpdateAction;
    private Disposable mScrollAction;

    public ChannelPresenter(Context context) {
        mContext = context;
        mMediaService = YouTubeMediaService.instance();
        mPlaybackPresenter = PlaybackPresenter.instance(context);
    }

    public static ChannelPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new ChannelPresenter(context.getApplicationContext());
        }

        return sInstance;
    }

    @Override
    public void onInitDone() {
        if (mChannelId != null) {
            mView.clear();
            updateRows(mChannelId);
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
            continueGroup(group);
        }
    }

    @Override
    public void register(ChannelView view) {
        mView = view;
    }

    @Override
    public void unregister(ChannelView view) {
        mView = null;

        disposeActions();
    }

    public void openChannel(Video item) {
        if (item != null) {
            openChannel(item.channelId);
        }
    }

    public void openChannel(String channelId) {
        if (channelId == null) {
            return;
        }

        disposeActions();
        ViewManager.instance(mContext).startView(ChannelView.class);

        if (mView != null) {
            mView.clear();
            updateRows(channelId);
        } else {
            mChannelId = channelId;
        }
    }

    private void disposeActions() {
        if (mUpdateAction != null && !mUpdateAction.isDisposed()) {
            mUpdateAction.dispose();
        }

        if (mScrollAction != null && !mScrollAction.isDisposed()) {
            mScrollAction.dispose();
        }
    }

    private void updateRows(String channelId) {
        if (channelId == null) {
            return;
        }

        Log.d(TAG, "updateRows: Start loading...");

        mView.showProgressBar(true);

        Observable<List<MediaGroup>> channelObserve = mMediaService.getMediaGroupManager().getChannelObserve(channelId);

        mUpdateAction = channelObserve
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::updateRowsHeader
                        , error -> Log.e(TAG, "updateRows error: " + error)
                        , () -> mView.showProgressBar(false));
    }

    private void updateRowsHeader(List<MediaGroup> mediaGroups) {
        for (MediaGroup mediaGroup : mediaGroups) {
            if (mediaGroup.getMediaItems() == null) {
                Log.e(TAG, "updateRowsHeader: MediaGroup is empty. Group Name: " + mediaGroup.getTitle());
                continue;
            }

            mView.update(VideoGroup.from(mediaGroup));
        }
    }

    private void continueGroup(VideoGroup group) {
        Log.d(TAG, "continueGroup: start continue group: " + group.getTitle());

        mView.showProgressBar(true);

        MediaGroup mediaGroup = group.getMediaGroup();

        MediaGroupManager mediaGroupManager = mMediaService.getMediaGroupManager();

        mScrollAction = mediaGroupManager.continueGroupObserve(mediaGroup)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(continueMediaGroup -> {
                            mView.update(VideoGroup.from(continueMediaGroup));
                        }, error -> Log.e(TAG, "continueGroup error: " + error),
                        () -> mView.showProgressBar(false));
    }
}
