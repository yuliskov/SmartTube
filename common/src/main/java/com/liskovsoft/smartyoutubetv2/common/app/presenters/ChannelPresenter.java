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
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.VideoMenuPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.utils.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.List;

public class ChannelPresenter extends BasePresenter<ChannelView> implements VideoGroupPresenter {
    private static final String TAG = ChannelPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static ChannelPresenter sInstance;
    private final MediaService mMediaService;
    private final PlaybackPresenter mPlaybackPresenter;
    private final MediaServiceManager mServiceManager;
    private String mChannelId;
    private Disposable mUpdateAction;
    private Disposable mScrollAction;

    public ChannelPresenter(Context context) {
        super(context);
        mMediaService = YouTubeMediaService.instance();
        mPlaybackPresenter = PlaybackPresenter.instance(context);
        mServiceManager = MediaServiceManager.instance();
    }

    public static ChannelPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new ChannelPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    @Override
    public void onViewInitialized() {
        if (mChannelId != null) {
            getView().clear();
            updateRows(mChannelId);
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
        VideoMenuPresenter.instance(getContext()).showVideoMenu(item);
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
            continueGroup(group);
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

    public static boolean canOpenChannel(Video item) {
        if (item == null) {
            return false;
        }

        return item.videoId != null || item.channelId != null || item.isChannelUploadsSection();
    }

    public void openChannel(Video item) {
        if (item != null) {
            if (item.channelId != null) {
                openChannel(item.channelId);
            } else if (item.videoId != null) {
                MessageHelpers.showLongMessage(getContext(), R.string.wait_data_loading);
                mServiceManager.loadMetadata(item, metadata -> {
                    openChannel(metadata.getChannelId());
                    item.channelId = metadata.getChannelId();
                });
            } else if (item.isChannelUploadsSection()) {
                // Maybe this is subscribed items view
                ChannelUploadsPresenter.instance(getContext())
                        .obtainVideoGroup(item, group -> {
                            openChannel(group.getChannelId());
                            item.channelId = group.getChannelId();
                        });
            }
        }
    }

    public void openChannel(String channelId) {
        if (channelId == null) {
            return;
        }

        disposeActions();

        mChannelId = channelId;

        if (getView() != null) {
            getView().clear();
            updateRows(mChannelId);
            // Fix double results. Prevent from doing the same in onViewInitialized()
            mChannelId = null;
        }

        ViewManager.instance(getContext()).startView(ChannelView.class);
    }

    private void disposeActions() {
        RxUtils.disposeActions(mUpdateAction, mScrollAction);
        mServiceManager.disposeActions();
    }

    private void updateRows(String channelId) {
        if (channelId == null) {
            return;
        }

        Log.d(TAG, "updateRows: Start loading...");

        getView().showProgressBar(true);

        Observable<List<MediaGroup>> channelObserve = mMediaService.getMediaGroupManager().getChannelObserve(channelId);

        mUpdateAction = channelObserve
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::updateRowsHeader,
                        error -> Log.e(TAG, "updateRows error: %s", error.getMessage())
                 );
    }

    private void updateRowsHeader(List<MediaGroup> mediaGroups) {
        for (MediaGroup mediaGroup : mediaGroups) {
            if (mediaGroup.getMediaItems() == null) {
                Log.e(TAG, "updateRowsHeader: MediaGroup is empty. Group Name: " + mediaGroup.getTitle());
                continue;
            }

            getView().update(VideoGroup.from(mediaGroup));
        }

        getView().showProgressBar(false);
    }

    private void continueGroup(VideoGroup group) {
        Log.d(TAG, "continueGroup: start continue group: " + group.getTitle());

        getView().showProgressBar(true);

        MediaGroup mediaGroup = group.getMediaGroup();

        MediaGroupManager mediaGroupManager = mMediaService.getMediaGroupManager();

        mScrollAction = mediaGroupManager.continueGroupObserve(mediaGroup)
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
}
