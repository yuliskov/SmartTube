package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs;

import android.content.Context;

import com.liskovsoft.sharedutils.helpers.DeviceHelpers;
import com.liskovsoft.sharedutils.helpers.DeviceHelpers.ArchType;

public class StableRestorePresenter extends BridgePresenter {
    private static final Integer[] STABLE_PKG_HASH = {-1}; // always re-install
    private static final String STABLE_PKG_NAME = "com.teamsmart.videomanager.tv";
    private static final String BETA_PKG_NAME = "com.liskovsoft.smarttubetv.beta";
    private static final String STABLE_PKG_URL_ARM = "https://github.com/yuliskov/SmartTube/releases/download/latest/smarttube_stable.apk";
    private static final String STABLE_PKG_URL_ARM64 = "https://github.com/yuliskov/SmartTube/releases/download/latest/smarttube_stable_arm64-v8a.apk";
    private static final String STABLE_PKG_URL_X86 = "https://github.com/yuliskov/SmartTube/releases/download/latest/smarttube_stable_x86.apk";
    private static StableRestorePresenter sInstance;

    public StableRestorePresenter(Context context) {
        super(context);
    }

    public static StableRestorePresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new StableRestorePresenter(context);
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
        ArchType archType = DeviceHelpers.getArchType();

        switch (archType) {
            case ARM_64:
                return STABLE_PKG_URL_ARM64;
            case X86_64:
            case X86:
                return STABLE_PKG_URL_X86;
            default:
                return STABLE_PKG_URL_ARM;
        }
    }

    @Override
    protected Integer[] getPackageSignatureHash() {
        return STABLE_PKG_HASH;
    }

    @Override
    protected boolean checkLauncher() {
        return true;
    }

    public boolean isSupported() {
        return getContext().getPackageName().equals(BETA_PKG_NAME);
    }
}
