package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.mylogger.Log;
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
    private ChannelView mView;
    private Disposable mUpdateAction;
    private String mChannelId;

    public ChannelPresenter(Context context) {
        mContext = context;
        mMediaService = YouTubeMediaService.instance();
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

    }

    @Override
    public void onVideoItemLongClicked(Video item) {

    }

    @Override
    public void onScrollEnd(VideoGroup group) {

    }

    @Override
    public void register(ChannelView view) {
        mView = view;
    }

    @Override
    public void unregister(ChannelView view) {
        mView = null;
    }

    public void openChannel(String channelId) {
        if (channelId == null) {
            return;
        }

        disposeUpdateAction();
        ViewManager.instance(mContext).startView(ChannelView.class);

        if (mView != null) {
            mView.clear();
            updateRows(channelId);
        } else {
            mChannelId = channelId;
        }
    }

    private void disposeUpdateAction() {
        boolean updateInProgress = mUpdateAction != null && !mUpdateAction.isDisposed();

        if (updateInProgress) {
            mUpdateAction.dispose();
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
}
