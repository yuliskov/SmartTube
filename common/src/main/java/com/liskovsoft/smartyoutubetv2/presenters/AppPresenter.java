package com.liskovsoft.smartyoutubetv2.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import com.liskovsoft.mediaserviceinterfaces.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.MediaGroupManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.views.AppView;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.schedulers.Schedulers;

import java.util.ArrayList;

public class AppPresenter extends Presenter<AppView> {
    private static final String TAG = AppPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static AppPresenter sInstance;
    private final Handler mHandler = new Handler();
    private final Context mContext;
    private final ArrayList<MediaGroup> mMediaGroups;

    private AppPresenter(Context context) {
        mMediaGroups = new ArrayList<>();
        mContext = context;
    }

    public static AppPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new AppPresenter(context);
        }

        return sInstance;
    }

    public void onInitDone() {
        if (!AppPrefs.instance(mContext).getCompletedOnboarding()) {
            // This is the first time running the app, let's go to onboarding
            for (AppView view : mViews) {
                view.showOnboarding();
            }
        }

        loadVideoData();
    }

    //private void updateRecommendations() {
    //    Intent recommendationIntent = new Intent(mContext, UpdateRecommendationsService.class);
    //    mContext.startService(recommendationIntent);
    //}
    
    @SuppressLint("CheckResult")
    private void loadVideoData() {
        MediaService service = YouTubeMediaService.instance();
        MediaGroupManager mediaGroupManager = service.getMediaGroupManager();

        mediaGroupManager.getHomeGroupObserve()
                .subscribeOn(Schedulers.newThread())
                .subscribe(nextMediaGroup -> {

            if (nextMediaGroup == null || nextMediaGroup.getNestedGroups() == null) {
                Log.e(TAG, "Home groups not found");
                return;
            }

            for (MediaGroup mediaGroup : nextMediaGroup.getNestedGroups()) {
                for (AppView view : mViews) {
                    view.addHomeGroup(mediaGroup);
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
                        .subscribe(nextMediaGroup -> {
                            for (AppView view : mViews) {
                                view.continueHomeGroup(mediaGroup);
                            }
                        });
            }
        });
    }
}
