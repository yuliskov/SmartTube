package com.liskovsoft.smartyoutubetv2.common.misc;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;

import com.liskovsoft.googleapi.oauth2.impl.GoogleSignInService;
import com.liskovsoft.googleapi.drive3.impl.GDriveService;
import com.liskovsoft.mediaserviceinterfaces.google.DriveService;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.GoogleSignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeSignInService;

import java.io.File;
import java.util.Collection;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class GDriveBackupManager {
    @SuppressLint("StaticFieldLeak")
    private static GDriveBackupManager sInstance;
    private final Context mContext;
    private static final String SHARED_PREFS_SUBDIR = "shared_prefs";
    private final GoogleSignInService mSignInService;
    private final DriveService mDriveService;
    private final File mDataDir;
    private final String mBackupDir;
    private Disposable mBackupAction;
    private Disposable mRestoreAction;

    private GDriveBackupManager(Context context) {
        mContext = context;
        mDataDir = new File(mContext.getApplicationInfo().dataDir, SHARED_PREFS_SUBDIR);
        mBackupDir = String.format("SmartTubeBackup/%s", context.getPackageName());
        mSignInService = GoogleSignInService.instance();
        mDriveService = GDriveService.instance();
    }

    public static GDriveBackupManager instance(Context context) {
        if (sInstance == null) {
            sInstance = new GDriveBackupManager(context);
        }

        return sInstance;
    }

    public void backup() {
        if (!YouTubeSignInService.instance().isSigned()) {
            return;
        }

        if (RxHelper.isAnyActionRunning(mBackupAction, mRestoreAction)) {
            MessageHelpers.showMessage(mContext, R.string.wait_data_loading);
            return;
        }

        if (mSignInService.isSigned()) {
            startBackup();
        } else {
            logIn(this::startBackup);
        }
    }

    public void restore() {
        if (RxHelper.isAnyActionRunning(mBackupAction, mRestoreAction)) {
            MessageHelpers.showMessage(mContext, R.string.wait_data_loading);
            return;
        }

        if (mSignInService.isSigned()) {
            startRestore();
        } else {
            logIn(this::startRestore);
        }
    }

    private void startBackup() {
        Collection<File> files = FileHelpers.listFileTree(mDataDir);

        mBackupAction = Observable.fromIterable(files)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io()) // run subscribe on separate thread
                .subscribe(file -> {
                    if (file.isFile()) {
                        RxHelper.runBlocking(mDriveService.uploadFile(file, Uri.parse(String.format("%s%s", mBackupDir,
                                file.getAbsolutePath().replace(mDataDir.getAbsolutePath(), "")))));

                        MessageHelpers.showLongMessage(mContext, file.getName());
                    }
                });
    }

    private void startRestore() {
        mRestoreAction = mDriveService.getList(Uri.parse(mBackupDir))
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io()) // run subscribe on separate thread
                .subscribe(paths -> {
                    for (String path : paths) {
                        mDriveService.getFile(Uri.parse(String.format("%s/%s", mBackupDir, path)))
                                .blockingSubscribe(inputStream -> FileHelpers.copy(inputStream, new File(mDataDir.getAbsolutePath(), path)));

                        MessageHelpers.showLongMessage(mContext, path);
                    }

                    MessageHelpers.showLongMessage(mContext, R.string.msg_done);
                    Utils.restartTheApp(mContext);
                });
    }

    private void logIn(Runnable onDone) {
        GoogleSignInPresenter.instance(mContext).start(onDone);
    }
}
