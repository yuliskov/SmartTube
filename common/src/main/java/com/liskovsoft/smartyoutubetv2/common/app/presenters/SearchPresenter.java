package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.MediaGroupManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class SearchPresenter extends BasePresenter<SearchView> implements VideoGroupPresenter {
    private static final String TAG = SearchPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static SearchPresenter sInstance;
    private final MediaService mMediaService;
    private final PlaybackPresenter mPlaybackPresenter;
    private final ViewManager mViewManager;
    private final SearchData mSearchData;
    private Disposable mScrollAction;
    private Disposable mLoadAction;
    private String mSearchText;

    private SearchPresenter(Context context) {
        super(context);
        mMediaService = YouTubeMediaService.instance();
        mPlaybackPresenter = PlaybackPresenter.instance(context);
        mViewManager = ViewManager.instance(context);
        mSearchData = SearchData.instance(context);
    }

    public static SearchPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new SearchPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    @Override
    public void onViewDestroyed() {
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
    public void onViewInitialized() {
        loadSuggestedKeywords();

        startSearchInt(mSearchText);
        mSearchText = null;
    }

    private void loadSuggestedKeywords() {
        // TODO: not implemented
    }

    @Override
    public void onVideoItemClicked(Video item) {
        if (getView() == null) {
            return;
        }

        if (item.isVideo()) {
            mPlaybackPresenter.openVideo(item);
        } else if (item.isChannel()) {
            ChannelPresenter.instance(getContext()).openChannel(item);
        }
    }

    @Override
    public void onVideoItemLongClicked(Video item) {
        if (getView() == null) {
            return;
        }

        VideoMenuPresenter.instance(getContext()).showMenu(item);
    }

    public void onSearch(String searchText) {
         loadSearchResult(searchText);
    }
    
    private void loadSearchResult(String searchText) {
        getView().showProgressBar(true);

        MediaGroupManager mediaGroupManager = mMediaService.getMediaGroupManager();

        getView().clearSearch();

        mLoadAction = mediaGroupManager.getSearchObserve(searchText)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(mediaGroup -> {
                    getView().updateSearch(VideoGroup.from(mediaGroup));
                }, error -> Log.e(TAG, "loadSearchData error: " + error),
                   () -> getView().showProgressBar(false));
    }
    
    private void continueGroup(VideoGroup group) {
        Log.d(TAG, "continueGroup: start continue group: " + group.getTitle());

        getView().showProgressBar(true);

        MediaGroup mediaGroup = group.getMediaGroup();

        MediaGroupManager mediaGroupManager = mMediaService.getMediaGroupManager();

        mScrollAction = mediaGroupManager.continueGroupObserve(mediaGroup)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(continueMediaGroup -> {
                    getView().updateSearch(VideoGroup.from(continueMediaGroup));
                }, error -> Log.e(TAG, "continueGroup error: " + error),
                   () -> getView().showProgressBar(false));
    }

    @Override
    public void onScrollEnd(VideoGroup group) {
        Log.d(TAG, "onScrollEnd: Group title: " + group.getTitle());

        boolean updateInProgress = mScrollAction != null && !mScrollAction.isDisposed();

        if (!updateInProgress) {
            continueGroup(group);
        }
    }

    public void startSearch(String searchText) {
        mSearchText = searchText;

        if (getView() == null) {
            mViewManager.startView(SearchView.class);
        } else {
            mViewManager.startView(SearchView.class);
            startSearchInt(searchText);
        }
    }

    private void startSearchInt(String searchText) {
        if (mSearchData.isInstantVoiceSearchEnabled() && searchText == null) {
            getView().startVoiceRecognition();
        } else {
            getView().startSearch(searchText);
        }
    }
}
