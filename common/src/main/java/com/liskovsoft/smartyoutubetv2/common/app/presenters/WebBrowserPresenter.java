package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.WebBrowserView;

public class WebBrowserPresenter extends BasePresenter<WebBrowserView> {
    @SuppressLint("StaticFieldLeak")
    private static WebBrowserPresenter sInstance;
    private String mUrl;

    private WebBrowserPresenter(Context context) {
        super(context);
    }

    public static WebBrowserPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new WebBrowserPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public static void unhold() {
        sInstance = null;
    }

    @Override
    public void onViewInitialized() {
        if (mUrl != null) {
            getView().loadUrl(mUrl);
        }
    }

    public void loadUrl(String url) {
        mUrl = url;

        getViewManager().startView(WebBrowserView.class);

        if (getView() != null) {
            getView().loadUrl(url);
        }
    }
}
