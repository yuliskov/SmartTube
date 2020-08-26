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

public class MainPresenter extends PresenterBase<MainView> {
    private static final String TAG = MainPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static MainPresenter sInstance;
    private final Handler mHandler = new Handler();
    private final Context mContext;
    private final ArrayList<MediaGroup> mMediaGroups;
    private final Map<Integer, Header> mHeaders = new HashMap<>();

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

    public void onInitDone() {
        if (!AppPrefs.instance(mContext).getCompletedOnboarding()) {
            // This is the first time running the app, let's go to onboarding
            for (MainView view : mViews) {
                view.showOnboarding();
            }
        }

        initHeaders();
        loadHomeData();
        loadSearchData();
    }

    public void onVideoItemClick(Video item) {
        for (MainView view : mViews) {
            view.openPlaybackView(item);
        }
    }

    public void onVideoItemLongClick(Video item) {
        for (MainView view : mViews) {
            view.openDetailsView(item);
        }
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

                for (MainView view : mViews) {
                    view.updateRowHeader(VideoGroup.from(mediaGroup), mHeaders.get(MediaGroup.TYPE_HOME));
                }

                mMediaGroups.add(mediaGroup);
            }
        },
        error -> {},
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

                            for (MainView view : mViews) {
                                view.updateRowHeader(VideoGroup.from(continueMediaGroup), mHeaders.get(MediaGroup.TYPE_HOME));
                            }
                        },
                        error -> {});
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
                    for (MainView view : mViews) {
                        view.updateRowHeader(VideoGroup.from(mediaGroup), mHeaders.get(MediaGroup.TYPE_SEARCH));
                    }
                }, error -> {});
    }
}
