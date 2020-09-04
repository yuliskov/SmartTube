package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaGroupManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class SearchPresenter implements VideoGroupPresenter<SearchView> {
    private static final String TAG = SearchPresenter.class.getSimpleName();
    private static SearchPresenter sInstance;
    private final Context mContext;
    private final MediaService mMediaService;
    private final PlaybackPresenter mPlaybackPresenter;
    private final ViewManager mViewManager;
    private final DetailsPresenter mDetailsPresenter;
    private SearchView mView;

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

        mPlaybackPresenter.openVideo(item);
    }

    @Override
    public void onVideoItemLongClicked(Video item) {
        if (mView == null) {
            return;
        }

        mDetailsPresenter.openVideo(item);
    }

    public void onSearchText(String searchText) {
         loadSearchData(searchText);
    }

    @SuppressLint("CheckResult")
    private void loadSearchData(String searchText) {
        MediaGroupManager mediaGroupManager = mMediaService.getMediaGroupManager();

        mView.clearSearch();

        mediaGroupManager.getSearchObserve(searchText)
                .subscribeOn(Schedulers.newThread())
                .subscribe(mediaGroup -> {
                    if (mediaGroup == null) {
                        Log.e(TAG, "Search result is empty");
                        return;
                    }

                    mView.updateSearch(VideoGroup.from(mediaGroup));
                }, error -> Log.e(TAG, "loadSearchData: " + error));
    }

    @SuppressLint("CheckResult")
    private void continueGroup(VideoGroup group) {
        // avoid to continue multiple times
        if (group.isContinued()) {
            return;
        } else {
            group.setContinued(true);
        }

        MediaGroup mediaGroup = group.getMediaGroup();

        MediaGroupManager mediaGroupManager = mMediaService.getMediaGroupManager();

        mediaGroupManager.continueGroupObserve(mediaGroup)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(continueMediaGroup -> {
                    if (continueMediaGroup == null) {
                        Log.e(TAG, "Can't continue group with name " + mediaGroup.getTitle());
                        return;
                    }

                    mView.updateSearch(VideoGroup.from(continueMediaGroup));
                }, error -> Log.e(TAG, "continueGroup: " + error));
    }

    @Override
    public void onScrollEnd(VideoGroup group) {
        Log.d(TAG, "onScrollEnd: Group title: " + group.getTitle());
        continueGroup(group);
    }
}
