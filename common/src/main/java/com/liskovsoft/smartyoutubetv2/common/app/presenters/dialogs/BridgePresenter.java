package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import com.liskovsoft.appupdatechecker2.other.downloadmanager.DownloadManagerTask;
import com.liskovsoft.appupdatechecker2.other.downloadmanager.DownloadManagerTask.DownloadListener;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.utils.LoadingManager;

abstract class BridgePresenter extends BasePresenter<Void> implements MotherActivity.OnResult {
    private static final String TAG = BridgePresenter.class.getSimpleName();
    private final GeneralData mGeneralData;
    private boolean mRemoveOldApkFirst;
    private String mBridgePath;
    private boolean mForceInstall;
    private final DownloadListener mListener = new DownloadListener() {
        @Override
        public void onDownloadCompleted(Uri uri) {
            mBridgePath = uri.getPath();

            if (mBridgePath != null) {
                if (mForceInstall) {
                    installBridgeIfNeeded();
                } else {
                    startDialog();
                }
            }
        }
    };

    public BridgePresenter(Context context) {
        super(context);

        mGeneralData = GeneralData.instance(context);
    }

    public void start() {
        if (!mGeneralData.isBridgeCheckEnabled()) {
            onFinish();
            return;
        }
        
        runBridgeInstaller(false);
    }

    private void startDialog() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        OptionItem updateCheckOption = UiOptionItem.from(
                getContext().getString(R.string.enable_voice_search),
                option -> installBridgeIfNeeded());

        settingsPresenter.appendSingleSwitch(
                UiOptionItem.from(getContext().getString(R.string.show_again),
                optionItem -> mGeneralData.setBridgeCheckEnabled(optionItem.isSelected()),
                mGeneralData.isBridgeCheckEnabled())
        );
        settingsPresenter.appendSingleButton(updateCheckOption);

        //settingsPresenter.setOnFinish(getOnFinish());
        settingsPresenter.showDialog(getContext().getString(R.string.enable_voice_search));
    }

    public void runBridgeInstaller(boolean force) {
        mForceInstall = force;

        if (!checkLauncher()) {
            onFinish();
            return;
        }

        if (mForceInstall) {
            LoadingManager.showLoading(getContext(), true);
        }

        // Original tube installed
        mRemoveOldApkFirst = isOldApkInstalled();
        // Download apk first and start dialog when download complete
        downloadPackageFromUrl(getContext(), getPackageUrl());
    }

    private boolean isOldApkInstalled() {
        PackageInfo info = getPackageSignature(getPackageName());

        return Helpers.isUserApp(info) && !Helpers.equalsAny(info.signatures[0].hashCode(), getPackageSignatureHash());
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

    private void downloadPackageFromUrl(Context context, String pkgUrl) {
        Log.d(TAG, "Installing bridge apk");

        DownloadManagerTask task = new DownloadManagerTask(mListener, context, pkgUrl);
        task.execute();
    }

    private void installBridgeFromPath(Context context) {
        Helpers.installPackage(context, mBridgePath);
    }

    @Override
    public void onResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Helpers.REMOVE_PACKAGE_CODE && !isOldApkInstalled()) {
            installBridgeFromPath(getContext());
        }
    }

    private void installBridgeIfNeeded() {
        LoadingManager.showLoading(getContext(), false);

        if (mRemoveOldApkFirst) {
            if (getContext() instanceof MotherActivity) {
                ((MotherActivity) getContext()).addOnResult(this);

                Helpers.removePackageAndGetResult((Activity) getContext(), getPackageName());
            }
        } else {
            installBridgeFromPath(getContext());
        }
    }

    protected abstract String getPackageName();
    protected abstract String getPackageUrl();
    protected abstract Integer[] getPackageSignatureHash();
    protected abstract boolean checkLauncher();
}
