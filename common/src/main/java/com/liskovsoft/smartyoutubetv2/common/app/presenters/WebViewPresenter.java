package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.WebView;

public class WebViewPresenter extends BasePresenter<WebView> {
    @SuppressLint("StaticFieldLeak")
    private static WebViewPresenter sInstance;

    public WebViewPresenter(Context context) {
        super(context);
    }

    public static WebViewPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new WebViewPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public static void unhold() {
        sInstance = null;
    }
}
