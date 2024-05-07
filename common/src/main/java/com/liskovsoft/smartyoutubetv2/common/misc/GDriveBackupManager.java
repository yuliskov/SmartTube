package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import com.liskovsoft.googleapi.service.GDriveService;
import com.liskovsoft.googleapi.service.GoogleSignInService;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.GoogleSignInPresenter;

public class GDriveBackupManager {
    private final Context mContext;
    private final String mBackupFolder;
    private final GoogleSignInService mSignInService;
    private final GDriveService mDriveService;

    public GDriveBackupManager(Context context) {
        mContext = context;
        mBackupFolder = String.format("SmartTubeBackup/%s", context.getPackageName());
        mSignInService = GoogleSignInService.instance();
        mDriveService = GDriveService.instance();
    }

    public void backup() {
        if (mSignInService.isSigned()) {
            startBackup();
        } else {
            logIn(this::startBackup);
        }
    }

    public void restore() {
        if (mSignInService.isSigned()) {
            startRestore();
        } else {
            logIn(this::startRestore);
        }
    }

    public boolean hasBackup() {
        return false;
    }

    private void startBackup() {

    }

    private void startRestore() {

    }

    private void logIn(Runnable onDone) {
        GoogleSignInPresenter.instance(mContext).start(onDone);
    }
}
