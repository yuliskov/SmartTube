package com.liskovsoft.smartyoutubetv2.common.mvp.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import com.liskovsoft.mediaserviceinterfaces.MediaGroupManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.Header;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.Video;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.mvp.views.MainView;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MainPresenter implements Presenter<MainView> {
    private static final String TAG = MainPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static MainPresenter sInstance;
    private final Handler mHandler = new Handler();
    private final Context mContext;
    private final ArrayList<MediaGroup> mMediaGroups;
    private final Map<Integer, Header> mHeaders = new HashMap<>();
    private MainView mView;

    private MainPresenter(Context context) {
        mMediaGroups = new ArrayList<>();
        mContext = context;
    }

    public static MainPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new MainPresenter(context);
        }

        return sInstance;
    }

    @Override
    public void onInitDone() {
        if (mView == null) {
            return;
        }

        if (!AppPrefs.instance(mContext).getCompletedOnboarding()) {
            // This is the first time running the app, let's go to onboarding
            mView.showOnboarding();
        }

        initHeaders();
        loadHomeData();
        loadSearchData();
    }

    @Override
    public void register(MainView view) {
        mView = view;
    }

    @Override
    public void unregister(MainView view) {
        mView = null;
    }

    public void onVideoItemClicked(Video item) {
        if (mView == null) {
            return;
        }

        mView.openPlaybackView(item);
    }

    public void onVideoItemLongPressed(Video item) {
        if (mView == null) {
            return;
        }

        mView.openDetailsView(item);
    }

    private void initHeaders() {
        mHeaders.put(MediaGroup.TYPE_HOME, new Header(MediaGroup.TYPE_HOME, mContext.getString(R.string.header_home)));
        mHeaders.put(MediaGroup.TYPE_SEARCH, new Header(MediaGroup.TYPE_SEARCH, mContext.getString(R.string.header_search)));
    }

    // TODO: implement Android TV channels
    //private void updateRecommendations() {
    //    Intent recommendationIntent = new Intent(mContext, UpdateRecommendationsService.class);
    //    mContext.startService(recommendationIntent);
    //}

    @SuppressLint("CheckResult")
    private void loadHomeData() {
        MediaService service = YouTubeMediaService.instance();
        MediaGroupManager mediaGroupManager = service.getMediaGroupManager();

        mediaGroupManager.getHomeObserve()
                .subscribeOn(Schedulers.newThread())
                .subscribe(mediaGroups -> {

            if (mediaGroups == null) {
                Log.e(TAG, "Home groups not found");
                return;
            }

            for (MediaGroup mediaGroup : mediaGroups) {
                if (mediaGroup.getMediaItems() == null) {
                    Log.e(TAG, "MediaGroup is empty: " + mediaGroup.getTitle());
                    continue;
                }

                mView.updateRowHeader(VideoGroup.from(mediaGroup), mHeaders.get(MediaGroup.TYPE_HOME));

                mMediaGroups.add(mediaGroup);
            }
        },
        error -> Log.e(TAG, error),
        () -> {
            // continue nested groups

            // TODO: How many times group should be continued? Maybe continue on demand?
            for (MediaGroup mediaGroup : mMediaGroups) {
                mediaGroupManager.continueGroupObserve(mediaGroup)
                        .subscribeOn(Schedulers.newThread())
                        .subscribe(continueMediaGroup -> {
                            if (continueMediaGroup == null) {
                                Log.e(TAG, "Next Home group is empty: " + mediaGroup.getTitle());
                                return;
                            }

                            mView.updateRowHeader(VideoGroup.from(continueMediaGroup), mHeaders.get(MediaGroup.TYPE_HOME));
                        },
                        error -> Log.e(TAG, error));
            }
        });
    }

    @SuppressLint("CheckResult")
    private void loadSearchData() {
        MediaService service = YouTubeMediaService.instance();
        MediaGroupManager mediaGroupManager = service.getMediaGroupManager();

        mediaGroupManager.getSearchObserve("Самый лучший фильм")
                .subscribeOn(Schedulers.newThread())
                .subscribe(mediaGroup -> {
                    mView.updateRowHeader(VideoGroup.from(mediaGroup), mHeaders.get(MediaGroup.TYPE_SEARCH));
                }, error -> Log.e(TAG, error));
    }
}
