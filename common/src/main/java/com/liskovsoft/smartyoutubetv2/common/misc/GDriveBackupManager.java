package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.net.Uri;

import com.liskovsoft.googleapi.service.GDriveService;
import com.liskovsoft.googleapi.service.GoogleSignInService;
import com.liskovsoft.mediaserviceinterfaces.google.DriveService;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.GoogleSignInPresenter;

import java.io.File;
import java.util.Collection;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class GDriveBackupManager {
    private final Context mContext;
    private static final String SHARED_PREFS_SUBDIR = "shared_prefs";
    private final GoogleSignInService mSignInService;
    private final DriveService mDriveService;
    private final File mDataDir;
    private final String mBackupDir;
    private Disposable mAction;

    public GDriveBackupManager(Context context) {
        mContext = context;
        mDataDir = new File(mContext.getApplicationInfo().dataDir, SHARED_PREFS_SUBDIR);
        mBackupDir = String.format("SmartTubeBackup/%s", context.getPackageName());
        mSignInService = GoogleSignInService.instance();
        mDriveService = GDriveService.instance();
    }

    public void backup() {
        RxHelper.disposeActions(mAction);

        if (mSignInService.isSigned()) {
            startBackup();
        } else {
            logIn(this::startBackup);
        }
    }

    public void restore() {
        RxHelper.disposeActions(mAction);

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
        Collection<File> files = FileHelpers.listFileTree(mDataDir);

        mAction = Observable.fromIterable(files)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io()) // run subscribe on separate thread
                .subscribe(file -> {
                    if (file.isFile())
                        RxHelper.runBlocking(mDriveService.uploadFile(file, Uri.parse(String.format("%s%s", mBackupDir,
                            file.getAbsolutePath().replace(mDataDir.getAbsolutePath(), "")))));
                });
    }

    private void startRestore() {

    }

    private void logIn(Runnable onDone) {
        GoogleSignInPresenter.instance(mContext).start(onDone);
    }
}
