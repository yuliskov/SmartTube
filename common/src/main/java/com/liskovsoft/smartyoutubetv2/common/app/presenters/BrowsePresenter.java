package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.telecom.Call;
import com.liskovsoft.mediaserviceinterfaces.MediaGroupManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.configparser.AssetPropertyParser2;
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
import io.reactivex.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

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

        initHeaders();
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
        continueGroup(group);
    }

    private void initHeaders() {
        MediaGroupManager mediaGroupManager = mMediaService.getMediaGroupManager();

        mHeaders.add(new Header(MediaGroup.TYPE_HOME, mContext.getString(R.string.header_home), Header.TYPE_ROW));
        mHeaders.add(new Header(MediaGroup.TYPE_MUSIC, mContext.getString(R.string.header_music), Header.TYPE_ROW));
        mHeaders.add(new Header(MediaGroup.TYPE_GAMING, mContext.getString(R.string.header_gaming), Header.TYPE_ROW));
        mHeaders.add(new Header(MediaGroup.TYPE_NEWS, mContext.getString(R.string.header_news), Header.TYPE_ROW));
        mHeaders.add(new Header(MediaGroup.TYPE_SUBSCRIPTIONS, mContext.getString(R.string.header_subscriptions)));
        mHeaders.add(new Header(MediaGroup.TYPE_HISTORY, mContext.getString(R.string.header_history)));

        mRowMapping.put(MediaGroup.TYPE_HOME, mediaGroupManager.getHomeObserve());
        mRowMapping.put(MediaGroup.TYPE_NEWS, mediaGroupManager.getNewsObserve());
        mRowMapping.put(MediaGroup.TYPE_MUSIC, mediaGroupManager.getMusicObserve());
        mRowMapping.put(MediaGroup.TYPE_GAMING, mediaGroupManager.getGamingObserve());

        mGridMapping.put(MediaGroup.TYPE_SUBSCRIPTIONS, mediaGroupManager.getSubscriptionsObserve());
        mGridMapping.put(MediaGroup.TYPE_HISTORY, mediaGroupManager.getHistoryObserve());

        for (Header header : mHeaders) {
            addHeader(header);
        }
    }

    @Override
    public void onHeaderFocused(long headerId) {
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

        groups
            .subscribeOn(Schedulers.newThread())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(mediaGroups -> {
                if (mediaGroups == null) {
                    Log.e(TAG, "loadRowsHeader: Groups not found for header: " + header.getTitle());
                    return;
                }

                Log.d(TAG, "loadRowsHeader: Loading groups for header: " + header.getTitle());

                for (MediaGroup mediaGroup : mediaGroups) {
                    if (mediaGroup.getMediaItems() == null) {
                        Log.e(TAG, "loadRowsHeader: MediaGroup is empty. Group Name: " + mediaGroup.getTitle());
                        continue;
                    }

                    mView.updateHeader(VideoGroup.from(mediaGroup, header));
                }

                mView.showProgressBar(false);
            }, error -> Log.e(TAG, "loadRowsHeader error: " + error + " Group Name: " + header.getTitle()));
    }

    @SuppressLint("CheckResult")
    private void loadGridHeader(Header header, Observable<MediaGroup> group) {
        if (group == null) {
            Log.e(TAG, "loadGridHeader: No observable for header: " + header.getTitle());
            return;
        }

        Log.d(TAG, "loadGridHeader: Start loading header: " + header.getTitle());

        mView.showProgressBar(true);

        group
            .subscribeOn(Schedulers.newThread())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(mediaGroup -> {
                if (mediaGroup == null) {
                    Log.e(TAG, "loadGridHeader: Can't obtain header: " + header.getTitle());
                    return;
                }

                mView.updateHeader(VideoGroup.from(mediaGroup, header));

                mView.showProgressBar(false);
            }, error -> Log.e(TAG, "loadGridHeader error: " + error + " Group Name: " + header.getTitle()));
    }

    @SuppressLint("CheckResult")
    private void continueGroup(VideoGroup group) {
        // avoid to continue multiple times
        if (group.isContinued()) {
            return;
        } else {
            group.setContinued(true);
        }

        mView.showProgressBar(true);

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

                    mView.updateHeader(VideoGroup.from(continueMediaGroup, group.getHeader()));

                    mView.showProgressBar(false);
        }, error -> Log.e(TAG, "continueGroup: " + error));
    }
}
