package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import com.liskovsoft.mediaserviceinterfaces.MediaGroupManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Header;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BrowsePresenter implements HeaderPresenter<BrowseView> {
    private static final String TAG = BrowsePresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static BrowsePresenter sInstance;
    private final Handler mHandler = new Handler();
    private final Context mContext;
    private final PlaybackPresenter mPlaybackPresenter;
    private final DetailsPresenter mDetailsPresenter;
    private final MediaService mMediaService;
    private final ViewManager mViewManager;
    private final OnboardingPresenter mOnboardingPresenter;
    private final SignInPresenter mSignInPresenter;
    private BrowseView mView;
    private final List<Header> mHeaders;
    private final Map<Integer, Observable<MediaGroup>> mGridMapping;
    private final Map<Integer, Observable<List<MediaGroup>>> mRowMapping;
    private Disposable mPendingUpdate;
    private Disposable mPendingScroll;

    private BrowsePresenter(Context context) {
        GlobalPreferences.instance(context); // auth token storage
        mContext = context;
        mPlaybackPresenter = PlaybackPresenter.instance(context);
        mDetailsPresenter = DetailsPresenter.instance(context);
        mOnboardingPresenter = OnboardingPresenter.instance(context);
        mSignInPresenter = SignInPresenter.instance(context);
        mMediaService = YouTubeMediaService.instance();
        mViewManager = ViewManager.instance(context);
        mHeaders = new ArrayList<>();
        mGridMapping = new HashMap<>();
        mRowMapping = new HashMap<>();
        initHeaders();
    }

    public static BrowsePresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new BrowsePresenter(context.getApplicationContext());
        }

        return sInstance;
    }

    @Override
    public void onInitDone() {
        if (mView == null) {
            return;
        }

        //mOnboardingPresenter.showOnboarding();

        //mSignInPresenter.checkUserIsSigned();

        addHeaders();
    }

    private void addHeaders() {
        for (Header header : mHeaders) {
            addHeader(header);
        }
    }

    private void addHeader(Header header) {
        mView.updateHeader(VideoGroup.from(header));
    }

    @Override
    public void register(BrowseView view) {
        mView = view;
    }

    @Override
    public void unregister(BrowseView view) {
        mView = null;
    }

    @Override
    public void onVideoItemClicked(Video item) {
        if (mView == null) {
            return;
        }

        mPlaybackPresenter.openVideo(mView, item);
    }

    @Override
    public void onVideoItemLongClicked(Video item) {
        if (mView == null) {
            return;
        }

        mDetailsPresenter.openVideo(item);
    }

    @Override
    public void onScrollEnd(VideoGroup group) {
        Log.d(TAG, "onScrollEnd. Group title: " + group.getTitle());

        boolean updateInProgress = mPendingScroll != null && !mPendingScroll.isDisposed();

        if (updateInProgress) {
            return;
        }

        continueGroup(group);
    }

    @Override
    public void onHeaderFocused(long headerId) {
        boolean updateInProgress = mPendingUpdate != null && !mPendingUpdate.isDisposed();

        if (updateInProgress) {
            mPendingUpdate.dispose();
        }

        for (Header header : mHeaders) {
            if (header.getId() == headerId) {
                mView.clearHeader(header);
                loadHeader(header);
            }
        }
    }

    private void loadHeader(Header header) {
        switch (header.getType()) {
            case Header.TYPE_GRID:
                loadGridHeader(header, mGridMapping.get(header.getId()));
                break;
            case Header.TYPE_ROW:
                loadRowsHeader(header, mRowMapping.get(header.getId()));
                break;
        }
    }

    private void initHeaders() {
        MediaGroupManager mediaGroupManager = mMediaService.getMediaGroupManager();

        mHeaders.add(new Header(MediaGroup.TYPE_HOME, mContext.getString(R.string.header_home), Header.TYPE_ROW, R.drawable.header_home));
        mHeaders.add(new Header(MediaGroup.TYPE_GAMING, mContext.getString(R.string.header_gaming)));
        mHeaders.add(new Header(MediaGroup.TYPE_NEWS, mContext.getString(R.string.header_news)));
        mHeaders.add(new Header(MediaGroup.TYPE_MUSIC, mContext.getString(R.string.header_music)));
        mHeaders.add(new Header(MediaGroup.TYPE_SUBSCRIPTIONS, mContext.getString(R.string.header_subscriptions), Header.TYPE_GRID));
        mHeaders.add(new Header(MediaGroup.TYPE_HISTORY, mContext.getString(R.string.header_history), Header.TYPE_GRID));
        mHeaders.add(new Header(MediaGroup.TYPE_PLAYLISTS, mContext.getString(R.string.header_playlists)));

        mRowMapping.put(MediaGroup.TYPE_HOME, mediaGroupManager.getHomeObserve());
        mRowMapping.put(MediaGroup.TYPE_NEWS, mediaGroupManager.getNewsObserve());
        mRowMapping.put(MediaGroup.TYPE_MUSIC, mediaGroupManager.getMusicObserve());
        mRowMapping.put(MediaGroup.TYPE_GAMING, mediaGroupManager.getGamingObserve());
        mRowMapping.put(MediaGroup.TYPE_PLAYLISTS, mediaGroupManager.getPlaylistsObserve());

        mGridMapping.put(MediaGroup.TYPE_SUBSCRIPTIONS, mediaGroupManager.getSubscriptionsObserve());
        mGridMapping.put(MediaGroup.TYPE_HISTORY, mediaGroupManager.getHistoryObserve());
    }

    // TODO: implement Android TV channels
    //private void updateRecommendations() {
    //    Intent recommendationIntent = new Intent(mContext, UpdateRecommendationsService.class);
    //    mContext.startService(recommendationIntent);
    //}

    @SuppressLint("CheckResult")
    private void loadRowsHeader(Header header, Observable<List<MediaGroup>> groups) {
        if (groups == null) {
            Log.e(TAG, "loadRowsHeader: No observable for header: " + header.getTitle());
            return;
        }

        Log.d(TAG, "loadRowsHeader: Start loading header: " + header.getTitle());

        mView.showProgressBar(true);

        mPendingUpdate = groups
            .subscribeOn(Schedulers.newThread())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(mediaGroups -> {
                Log.d(TAG, "loadRowsHeader: Loading groups for header: " + header.getTitle());

                for (MediaGroup mediaGroup : mediaGroups) {
                    if (mediaGroup.getMediaItems() == null) {
                        Log.e(TAG, "loadRowsHeader: MediaGroup is empty. Group Name: " + mediaGroup.getTitle());
                        continue;
                    }

                    mView.updateHeader(VideoGroup.from(mediaGroup, header));
                }
            }, error -> Log.e(TAG, "loadRowsHeader error: " + error + " Group Name: " + header.getTitle())
            , () -> mView.showProgressBar(false));
    }

    @SuppressLint("CheckResult")
    private void loadGridHeader(Header header, Observable<MediaGroup> group) {
        if (group == null) {
            Log.e(TAG, "loadGridHeader: No observable for header: " + header.getTitle());
            return;
        }

        Log.d(TAG, "loadGridHeader: Start loading header: " + header.getTitle());

        mView.showProgressBar(true);

        mPendingUpdate = group
            .subscribeOn(Schedulers.newThread())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(mediaGroup -> {
                mView.updateHeader(VideoGroup.from(mediaGroup, header));
            }, error -> Log.e(TAG, "loadGridHeader error: " + error + " Group Name: " + header.getTitle())
            , () -> mView.showProgressBar(false));
    }

    @SuppressLint("CheckResult")
    private void continueGroup(VideoGroup group) {
        Log.d(TAG, "continueGroup: start continue group: " + group.getTitle());

        mView.showProgressBar(true);

        MediaGroup mediaGroup = group.getMediaGroup();

        MediaGroupManager mediaGroupManager = mMediaService.getMediaGroupManager();

        mPendingScroll = mediaGroupManager.continueGroupObserve(mediaGroup)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(continueMediaGroup -> {
                    if (continueMediaGroup == null) {
                        Log.e(TAG, "Can't continue group with name " + mediaGroup.getTitle());
                        return;
                    }

                    mView.updateHeader(VideoGroup.from(continueMediaGroup, group.getHeader()));
        }, error -> Log.e(TAG, "continueGroup: " + error)
        , () -> mView.showProgressBar(false));
    }
}
