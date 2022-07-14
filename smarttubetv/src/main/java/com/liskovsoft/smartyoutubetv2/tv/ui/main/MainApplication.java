package com.liskovsoft.smartyoutubetv2.tv.ui.main;

import androidx.multidex.MultiDexApplication;
import com.liskovsoft.smartyoutubetv2.common.app.views.AddDeviceView;
import com.liskovsoft.smartyoutubetv2.common.app.views.AppDialogView;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
import com.liskovsoft.smartyoutubetv2.common.app.views.DetailsView;
import com.liskovsoft.smartyoutubetv2.common.app.views.OnboardingView;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.app.views.WebBrowserView;
import com.liskovsoft.smartyoutubetv2.tv.ui.adddevice.AddDeviceActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.BrowseActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.channel.ChannelActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.channeluploads.ChannelUploadsActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.details.VideoDetailsActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.AppDialogActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.onboarding.OnboardingActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.search.tags.SearchTagsActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.signin.SignInActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.webbrowser.WebBrowserActivity;

public class MainApplication extends MultiDexApplication { // fix: Didn't find class "com.google.firebase.provider.FirebaseInitProvider"
    static {
        // fix youtube bandwidth throttling
        System.setProperty("http.keepAlive", "false");
        // fix ipv6 infinite video buffering???
        System.setProperty("java.net.preferIPv6Addresses", "false");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Android 4 SponsorBlock fix???
        // https://android-review.googlesource.com/c/platform/external/conscrypt/+/89408/
        //if (Build.VERSION.SDK_INT == 19) {
        //    Security.insertProviderAt(Conscrypt.newProvider(), 1);
        //}

        setupViewManager();
    }

    private void setupViewManager() {
        ViewManager viewManager = ViewManager.instance(this);

        viewManager.setRoot(BrowseActivity.class);
        viewManager.register(SplashView.class, SplashActivity.class); // no parent, because it's root activity
        viewManager.register(BrowseView.class, BrowseActivity.class); // no parent, because it's root activity
        viewManager.register(PlaybackView.class, PlaybackActivity.class, BrowseActivity.class);
        viewManager.register(AppDialogView.class, AppDialogActivity.class, PlaybackActivity.class);
        viewManager.register(OnboardingView.class, OnboardingActivity.class, BrowseActivity.class);
        viewManager.register(DetailsView.class, VideoDetailsActivity.class, BrowseActivity.class);
        viewManager.register(SearchView.class, SearchTagsActivity.class, BrowseActivity.class);
        viewManager.register(SignInView.class, SignInActivity.class, BrowseActivity.class);
        viewManager.register(AddDeviceView.class, AddDeviceActivity.class, BrowseActivity.class);
        viewManager.register(ChannelView.class, ChannelActivity.class, BrowseActivity.class);
        viewManager.register(ChannelUploadsView.class, ChannelUploadsActivity.class, BrowseActivity.class);
        viewManager.register(WebBrowserView.class, WebBrowserActivity.class, BrowseActivity.class);
    }
}
