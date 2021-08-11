package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import com.liskovsoft.appupdatechecker2.other.downloadmanager.DownloadManagerTask;
import com.liskovsoft.appupdatechecker2.other.downloadmanager.DownloadManagerTask.DownloadListener;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;

abstract class BridgePresenter extends BasePresenter<Void> {
    private static final String TAG = BridgePresenter.class.getSimpleName();
    private boolean mRemoveOldApkFirst;
    private final DownloadListener listener = new DownloadListener() {
        @Override
        public void onDownloadCompleted(Uri uri) {
            Helpers.installPackage(getContext(), uri.getPath());
        }
    };

    public BridgePresenter(Context context) {
        super(context);
    }

    public void start() {
        
    }

    private void runBridgeInstaller() {
        if (!checkLauncher()) {
            return;
        }

        PackageInfo info = getPackageSignature(getPackageName());

        if (info != null) { // bridge installed
            if (Helpers.isUserApp(info) && info.signatures[0].hashCode() != getPackageSignatureHash()) {
                // Original YouTube installed
                mRemoveOldApkFirst = true;
                installBridgeIfNeeded();
            }
        } else { // bridge not installed
            installBridgeIfNeeded();
        }
    }

    private PackageInfo getPackageSignature(String pkgName) {
        PackageManager manager = getContext().getPackageManager();
        PackageInfo packageInfo = null;

        try {
            packageInfo = manager.getPackageInfo(pkgName, PackageManager.GET_SIGNATURES);
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }

        return packageInfo;
    }

    private void installPackageFromUrl(Context context, String pkgUrl) {
        Log.d(TAG, "Installing bridge apk");

        DownloadManagerTask task = new DownloadManagerTask(listener, context, pkgUrl);
        task.execute();
    }

    //@Override
    //public void onActivityResult(int requestCode, int resultCode, Intent data) {
    //    if (requestCode == Helpers.REMOVE_PACKAGE_CODE) {
    //        installPackageFromUrl(mContext, getPackageUrl());
    //    }
    //}

    private void installBridgeIfNeeded() {
        if (mRemoveOldApkFirst) {
            if (getContext() instanceof Activity) {
                Helpers.removePackageAndGetResult((Activity) getContext(), getPackageName());
            }
        } else {
            installPackageFromUrl(getContext(), getPackageUrl());
        }
    }

    protected abstract String getPackageName();
    protected abstract String getPackageUrl();
    protected abstract int getPackageSignatureHash();
    protected abstract boolean checkLauncher();
}
