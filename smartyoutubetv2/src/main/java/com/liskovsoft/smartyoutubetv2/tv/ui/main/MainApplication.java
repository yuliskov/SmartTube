package com.liskovsoft.smartyoutubetv2.tv.ui.main;

import android.app.Application;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.app.views.DetailsView;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.OnboardingView;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.BrowseActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.details.VideoDetailsActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.onboarding.OnboardingActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.search.SearchActivity;

public class MainApplication extends Application {
    private static Class<?> sLastActivity;

    @Override
    public void onCreate() {
        super.onCreate();

        ViewManager viewManager = ViewManager.instance(this);

        viewManager.register(BrowseView.class, BrowseActivity.class); // no parent, because it's root activity
        viewManager.register(PlaybackView.class, PlaybackActivity.class, BrowseActivity.class);
        viewManager.register(OnboardingView.class, OnboardingActivity.class, BrowseActivity.class);
        viewManager.register(DetailsView.class, VideoDetailsActivity.class, BrowseActivity.class);
        viewManager.register(SearchView.class, SearchActivity.class, BrowseActivity.class);
    }

    public static void setLastActivity(Class<?> lastActivity) {
        sLastActivity = lastActivity;
    }

    public static Class<?> getLastActivity() {
        return sLastActivity;
    }
}
