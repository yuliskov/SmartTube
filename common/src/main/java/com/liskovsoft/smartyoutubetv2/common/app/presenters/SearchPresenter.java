package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaGroupManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class SearchPresenter implements VideoGroupPresenter<SearchView> {
    private static final String TAG = SearchPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static SearchPresenter sInstance;
    private final Context mContext;
    private final MediaService mMediaService;
    private final PlaybackPresenter mPlaybackPresenter;
    private final ViewManager mViewManager;
    private final DetailsPresenter mDetailsPresenter;
    private SearchView mView;
    private Disposable mScrollAction;
    private Disposable mLoadAction;

    private SearchPresenter(Context context) {
        mContext = context;
        mMediaService = YouTubeMediaService.instance();
        mPlaybackPresenter = PlaybackPresenter.instance(context);
        mDetailsPresenter = DetailsPresenter.instance(context);
        mViewManager = ViewManager.instance(context);
    }

    public static SearchPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new SearchPresenter(context.getApplicationContext());
        }

        return sInstance;
    }

    @Override
    public void register(SearchView view) {
        mView = view;
    }

    @Override
    public void unregister(SearchView view) {
        mView = null;

        disposeActions();
    }

    private void disposeActions() {
        if (mScrollAction != null && !mScrollAction.isDisposed()) {
            mScrollAction.dispose();
        }

        if (mLoadAction != null && !mLoadAction.isDisposed()) {
            mLoadAction.dispose();
        }
    }

    @Override
    public void onInitDone() {
        loadSuggestedKeywords();
    }

    private void loadSuggestedKeywords() {
        // TODO: not implemented
    }

    @Override
    public void onVideoItemClicked(Video item) {
        if (mView == null) {
            return;
        }

        if (item.isVideo()) {
            mPlaybackPresenter.openVideo(mView, item);
        } else if (item.isChannel()) {
            ChannelPresenter.instance(mContext).openChannel(item);
        }
    }

    @Override
    public void onVideoItemLongClicked(Video item) {
        if (mView == null) {
            return;
        }

        MessageHelpers.showMessage(mContext, R.string.not_implemented);

        //mDetailsPresenter.openVideo(item);
    }

    public void onSearchText(String searchText) {
         loadSearchResult(searchText);
    }
    
    private void loadSearchResult(String searchText) {
        mView.showProgressBar(true);

        MediaGroupManager mediaGroupManager = mMediaService.getMediaGroupManager();

        mView.clearSearch();

        mLoadAction = mediaGroupManager.getSearchObserve(searchText)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(mediaGroup -> {
                    mView.updateSearch(VideoGroup.from(mediaGroup));
                }, error -> Log.e(TAG, "loadSearchData error: " + error),
                   () -> mView.showProgressBar(false));
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
                    mView.updateSearch(VideoGroup.from(continueMediaGroup));
                }, error -> Log.e(TAG, "continueGroup error: " + error),
                   () -> mView.showProgressBar(false));
    }

    @Override
    public void onScrollEnd(VideoGroup group) {
        Log.d(TAG, "onScrollEnd: Group title: " + group.getTitle());

        boolean updateInProgress = mScrollAction != null && !mScrollAction.isDisposed();

        if (!updateInProgress) {
            continueGroup(group);
        }
    }
}
