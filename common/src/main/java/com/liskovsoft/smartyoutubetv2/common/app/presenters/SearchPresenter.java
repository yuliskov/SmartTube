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
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.VideoMenuPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;
import com.liskovsoft.smartyoutubetv2.common.utils.RxUtils;
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
        super.onViewDestroyed();
        disposeActions();
    }

    @Override
    public void onViewInitialized() {
        startSearchInt(mSearchText);
        mSearchText = null;
    }

    @Override
    public void onVideoItemSelected(Video item) {
        // NOP
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

        if (item.isVideo()) {
            VideoMenuPresenter.instance(getContext()).showVideoMenu(item);
        } else if (item.isChannel()) {
            VideoMenuPresenter.instance(getContext()).showChannelMenu(item);
        }
    }

    @Override
    public boolean hasPendingActions() {
        return RxUtils.isAnyActionRunning(mLoadAction, mScrollAction);
    }

    public void onSearch(String searchText) {
        if (getView() == null) {
            Log.e(TAG, "Search view has been unloaded from the memory. Low RAM?");
            startSearch(searchText);
            return;
        }

        loadSearchResult(searchText);
    }
    
    private void loadSearchResult(String searchText) {
        disposeActions();
        getView().showProgressBar(true);

        MediaGroupManager mediaGroupManager = mMediaService.getMediaGroupManager();

        getView().clearSearch();

        mLoadAction = mediaGroupManager.getSearchObserve(searchText)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        mediaGroup -> getView().updateSearch(VideoGroup.from(mediaGroup)),
                        error -> Log.e(TAG, "loadSearchData error: %s", error.getMessage()),
                        () -> getView().showProgressBar(false)
                );
    }
    
    private void continueGroup(VideoGroup group) {
        if (RxUtils.isAnyActionRunning(mScrollAction)) {
            return;
        }

        Log.d(TAG, "continueGroup: start continue group: " + group.getTitle());

        getView().showProgressBar(true);

        MediaGroup mediaGroup = group.getMediaGroup();

        MediaGroupManager mediaGroupManager = mMediaService.getMediaGroupManager();

        mScrollAction = mediaGroupManager.continueGroupObserve(mediaGroup)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        continueMediaGroup -> getView().updateSearch(VideoGroup.from(continueMediaGroup)),
                        error -> Log.e(TAG, "continueGroup error: %s", error.getMessage()),
                        () -> getView().showProgressBar(false)
                );
    }

    @Override
    public void onScrollEnd(Video item) {
        if (item == null) {
            Log.e(TAG, "Can't scroll. Video is null.");
            return;
        }

        VideoGroup group = item.group;

        Log.d(TAG, "onScrollEnd: Group title: " + group.getTitle());

        continueGroup(group);
    }

    public void startSearch(String searchText) {
        mSearchText = searchText;

        mViewManager.startView(SearchView.class);
        startSearchInt(searchText);
    }

    private void startSearchInt(String searchText) {
        if (getView() == null) {
            return;
        }

        if (mSearchData.isInstantVoiceSearchEnabled() && searchText == null) {
            getView().startVoiceRecognition();
        } else {
            getView().startSearch(searchText);
        }
    }

    private void disposeActions() {
        RxUtils.disposeActions(mLoadAction, mScrollAction);
    }
}
