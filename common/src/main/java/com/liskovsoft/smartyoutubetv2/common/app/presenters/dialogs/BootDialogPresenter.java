package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;

/**
 * Shows boot dialogs one by one.
 */
public class BootDialogPresenter extends BasePresenter<Void> {
    @SuppressLint("StaticFieldLeak")
    private static BootDialogPresenter sInstance;

    public BootDialogPresenter(Context context) {
        super(context);
    }

    public static BootDialogPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new BootDialogPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    //public void unhold() {
    //    sInstance = null;
    //}

    public void start() {
        startUpdatePresenter();
    }

    private void startUpdatePresenter() {
        AppUpdatePresenter updatePresenter = AppUpdatePresenter.instance(getContext());
        //updatePresenter.setOnDone(this::startBridgePresenter);
        updatePresenter.start(false);
        //updatePresenter.unhold();
    }

    //private void startBackupPresenter() {
    //    QuickRestorePresenter quickRestorePresenter = QuickRestorePresenter.instance(getContext());
    //    quickRestorePresenter.setOnDone(this::startBridgePresenter);
    //    quickRestorePresenter.start();
    //    quickRestorePresenter.unhold();
    //}

    //private void startBridgePresenter() {
    //    ATVBridgePresenter atvPresenter = ATVBridgePresenter.instance(getContext());
    //    atvPresenter.start();
    //    atvPresenter.unhold();
    //
    //    AmazonBridgePresenter amazonPresenter = AmazonBridgePresenter.instance(getContext());
    //    amazonPresenter.start();
    //    amazonPresenter.unhold();
    //}
}
