package com.liskovsoft.smartyoutubetv2.tv.ui.main;

import androidx.multidex.MultiDexApplication;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.AddDeviceView;
import com.liskovsoft.smartyoutubetv2.common.app.views.AppDialogView;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
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
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.AppDialogActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.search.tags.SearchTagsActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.signin.SignInActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.webbrowser.WebBrowserActivity;

import java.lang.Thread.UncaughtExceptionHandler;

public class MainApplication extends MultiDexApplication { // fix: Didn't find class "com.google.firebase.provider.FirebaseInitProvider"
    static {
        // fix youtube bandwidth throttling (best - false)???
        // false is better for streams (less buffering)
        System.setProperty("http.keepAlive", "false");
        // fix ipv6 infinite video buffering???
        // Better to remove this fix at all. Users complain about infinite loading.
        //System.setProperty("java.net.preferIPv6Addresses", "true");
        // Another IPv6 fix (no effect)
        // https://stackoverflow.com/questions/1920623/sometimes-httpurlconnection-getinputstream-executes-too-slowly
        //System.setProperty("java.net.preferIPv4Stack" , "true");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Android 4 SponsorBlock fix???
        // https://android-review.googlesource.com/c/platform/external/conscrypt/+/89408/
        //if (Build.VERSION.SDK_INT == 19) {
        //    Security.insertProviderAt(Conscrypt.newProvider(), 1);
        //}

        //setupGlobalExceptionHandler();
        setupViewManager();
    }

    private void setupViewManager() {
        ViewManager viewManager = ViewManager.instance(this);

        viewManager.setRoot(BrowseActivity.class);
        viewManager.register(SplashView.class, SplashActivity.class); // no parent, because it's root activity
        viewManager.register(BrowseView.class, BrowseActivity.class); // no parent, because it's root activity
        viewManager.register(PlaybackView.class, PlaybackActivity.class, BrowseActivity.class);
        viewManager.register(AppDialogView.class, AppDialogActivity.class, PlaybackActivity.class);
        viewManager.register(SearchView.class, SearchTagsActivity.class, BrowseActivity.class);
        viewManager.register(SignInView.class, SignInActivity.class, BrowseActivity.class);
        viewManager.register(AddDeviceView.class, AddDeviceActivity.class, BrowseActivity.class);
        viewManager.register(ChannelView.class, ChannelActivity.class, BrowseActivity.class);
        viewManager.register(ChannelUploadsView.class, ChannelUploadsActivity.class, BrowseActivity.class);
        viewManager.register(WebBrowserView.class, WebBrowserActivity.class, BrowseActivity.class);
    }

    private void setupGlobalExceptionHandler() {
        UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();

        if (defaultHandler == null) {
            return;
        }

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            if (Helpers.equalsAny(e.getMessage(),
                    "parameter must be a descendant of this view",
                    "Attempt to invoke virtual method 'android.view.ViewGroup$LayoutParams android.view.View.getLayoutParams()' on a null object reference")) {
                Class<?> view = ViewManager.instance(getApplicationContext()).getTopView();
                BrowseSection section = null;

                if (view == BrowseView.class) {
                    section = BrowsePresenter.instance(getApplicationContext()).getCurrentSection();
                }

                e = new RuntimeException("A crash in the view " + view.getSimpleName() + ", section id " + (section != null ? section.getId() : "-1"), e);
            }

            defaultHandler.uncaughtException(t, e);
        });
    }
}
