package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs;

import android.content.Context;
import com.liskovsoft.sharedutils.helpers.Helpers;

public class ATVBridgePresenter extends BridgePresenter {
    private static final int ATV_YOUTUBE_PKG_HASH = 1430778939;
    private static final String ATV_YOUTUBE_PKG_NAME = "com.google.android.youtube.tv";
    private static final String ATV_BRIDGE_PKG_URL = "https://github.com/yuliskov/SmartYouTubeTV/releases/download/stable/ATV_SYTV_Bridge.apk";
    private static ATVBridgePresenter sInstance;

    public ATVBridgePresenter(Context context) {
        super(context);
    }

    public static ATVBridgePresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new ATVBridgePresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public static void unhold() {
        sInstance = null;
    }

    @Override
    protected String getPackageName() {
        return ATV_YOUTUBE_PKG_NAME;
    }

    @Override
    protected String getPackageUrl() {
        return ATV_BRIDGE_PKG_URL;
    }

    @Override
    protected int getPackageSignatureHash() {
        return ATV_YOUTUBE_PKG_HASH;
    }

    @Override
    protected boolean checkLauncher() {
        return Helpers.isAndroidTVLauncher(getContext());
    }
}
