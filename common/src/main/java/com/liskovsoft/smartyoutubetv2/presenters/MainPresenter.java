package com.liskovsoft.smartyoutubetv2.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import com.liskovsoft.mediaserviceinterfaces.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.MediaGroupManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.views.MainView;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.schedulers.Schedulers;

import java.util.ArrayList;

public class MainPresenter extends Presenter<MainView> {
    private static final String TAG = MainPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static MainPresenter sInstance;
    private final Handler mHandler = new Handler();
    private final Context mContext;
    private final ArrayList<MediaGroup> mMediaGroups;

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

        loadHomeData();
    }

    //private void updateRecommendations() {
    //    Intent recommendationIntent = new Intent(mContext, UpdateRecommendationsService.class);
    //    mContext.startService(recommendationIntent);
    //}
    
    @SuppressLint("CheckResult")
    private void loadHomeData() {
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
                for (MainView view : mViews) {
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
                            for (MainView view : mViews) {
                                view.continueHomeGroup(mediaGroup);
                            }
                        });
            }
        });
    }
}
