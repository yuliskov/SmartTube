package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs;

import android.content.Context;
import com.liskovsoft.sharedutils.helpers.Helpers;

public class AmazonBridgePresenter extends BridgePresenter {
    private static final int AMAZON_YOUTUBE_PKG_HASH = 1430778939;
    private static final String AMAZON_YOUTUBE_PKG_NAME = "com.amazon.firetv.youtube";
    private static final String AMAZON_BRIDGE_PKG_URL = "https://github.com/yuliskov/SmartYouTubeTV/releases/download/stable/Amazon_SYTV_Bridge.apk";
    private static AmazonBridgePresenter sInstance;

    public AmazonBridgePresenter(Context context) {
        super(context);
    }

    public static AmazonBridgePresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new AmazonBridgePresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public static void unhold() {
        sInstance = null;
    }

    @Override
    protected String getPackageName() {
        return AMAZON_YOUTUBE_PKG_NAME;
    }

    @Override
    protected String getPackageUrl() {
        return AMAZON_BRIDGE_PKG_URL;
    }

    @Override
    protected int getPackageSignatureHash() {
        return AMAZON_YOUTUBE_PKG_HASH;
    }

    @Override
    protected boolean checkLauncher() {
        return Helpers.isAmazonFireTVDevice();
    }
}
