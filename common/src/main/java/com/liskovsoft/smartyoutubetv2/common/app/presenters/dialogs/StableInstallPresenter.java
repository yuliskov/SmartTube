package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs;

import android.content.Context;

import com.liskovsoft.sharedutils.helpers.Helpers;

public class StableInstallPresenter extends BridgePresenter {
    private static final Integer[] STABLE_PKG_HASH = {-1}; // always re-install
    private static final String STABLE_PKG_NAME = "com.teamsmart.videomanager.tv";
    private static final String BETA_PKG_NAME = "com.liskovsoft.smarttubetv.beta";
    private static final String STABLE_PKG_URL_ARM = "https://github.com/yuliskov/SmartTube/releases/download/latest/smarttube_stable.apk";
    private static final String STABLE_PKG_URL_ARM64 = "https://github.com/yuliskov/SmartTube/releases/download/latest/smarttube_stable_arm64-v8a.apk";
    private static final String STABLE_PKG_URL_X86 = "https://github.com/yuliskov/SmartTube/releases/download/latest/smarttube_stable_x86.apk";
    private static StableInstallPresenter sInstance;

    public StableInstallPresenter(Context context) {
        super(context);
    }

    public static StableInstallPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new StableInstallPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public void unhold() {
        sInstance = null;
    }

    @Override
    protected String getPackageName() {
        return STABLE_PKG_NAME;
    }

    @Override
    protected String getPackageUrl() {
        //String primaryAbi = DeviceHelpers.getPrimaryAbi();

        return STABLE_PKG_URL_ARM;
    }

    @Override
    protected Integer[] getPackageSignatureHash() {
        return STABLE_PKG_HASH;
    }

    @Override
    protected boolean checkLauncher() {
        return Helpers.isAndroidTVLauncher(getContext());
    }

    public boolean isSupported() {
        return getContext().getPackageName().equals(BETA_PKG_NAME);
    }
}
