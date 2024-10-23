package com.liskovsoft.smartyoutubetv2.common.misc;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Build;

import com.liskovsoft.googleapi.drive3.impl.GDriveService;
import com.liskovsoft.googleapi.oauth2.impl.GoogleSignInService;
import com.liskovsoft.mediaserviceinterfaces.google.DriveService;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.GoogleSignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.io.File;
import java.util.Collection;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class GDriveBackupManager {
    @SuppressLint("StaticFieldLeak")
    private static GDriveBackupManager sInstance;
    private final Context mContext;
    private static final String SHARED_PREFS_SUBDIR = "shared_prefs";
    private static final String BACKUP_NAME = "backup.zip";
    private final GoogleSignInService mSignInService;
    private final DriveService mDriveService;
    private final String mDataDir;
    private final String mBackupDir;
    private final GeneralData mGeneralData;
    private Disposable mBackupAction;
    private Disposable mRestoreAction;
    private final String[] mBackupNames;
    private boolean mIsBlocking;

    private GDriveBackupManager(Context context) {
        mContext = context;
        mGeneralData = GeneralData.instance(context);
        mDataDir = String.format("%s/%s", mContext.getApplicationInfo().dataDir, SHARED_PREFS_SUBDIR);
        mBackupDir = String.format("SmartTubeBackup/%s", context.getPackageName());
        mSignInService = GoogleSignInService.instance();
        mDriveService = GDriveService.instance();
        mBackupNames = new String[] {
                "search_tag_storage.xml",
                "com.liskovsoft.appupdatechecker2.preferences.xml",
                "com.liskovsoft.sharedutils.prefs.GlobalPreferences.xml",
                "_preferences.xml" // before _ should be the app package name
        };
    }

    public static GDriveBackupManager instance(Context context) {
        if (sInstance == null) {
            sInstance = new GDriveBackupManager(context);
        }

        return sInstance;
    }

    public static void unhold() {
        sInstance = null;
    }

    public void backup() {
        mIsBlocking = false;
        backupInt();
    }

    public void backupBlocking() {
        mIsBlocking = true;
        backupInt();
    }

    private void backupInt() {
        if (mIsBlocking && !mSignInService.isSigned()) {
            return;
        }

        if (RxHelper.isAnyActionRunning(mBackupAction, mRestoreAction)) {
            if (!mIsBlocking)
                MessageHelpers.showMessage(mContext, R.string.wait_data_loading);
            return;
        }

        if (mSignInService.isSigned()) {
            startBackupConfirm();
        } else {
            logIn(this::startBackupConfirm);
        }
    }

    public void restore() {
        if (RxHelper.isAnyActionRunning(mBackupAction, mRestoreAction)) {
            MessageHelpers.showMessage(mContext, R.string.wait_data_loading);
            return;
        }

        if (mSignInService.isSigned()) {
            startRestoreConfirm();
        } else {
            logIn(this::startRestoreConfirm);
        }
    }

    private void startBackupConfirm() {
        if (!mIsBlocking) {
            AppDialogUtil.showConfirmationDialog(mContext, mContext.getString(R.string.app_backup), this::startBackup);
        } else {
            startBackup();
        }
    }

    private void startBackup() {
        String backupDir = getBackupDir();
        startBackup2(backupDir, mDataDir);
    }

    private void startBackup(String backupDir, String dataDir) {
        Collection<File> files = FileHelpers.listFileTree(new File(dataDir));

        Consumer<File> backupConsumer = file -> {
            if (file.isFile()) {
                if (checkFileName(file.getName())) {
                    if (!mIsBlocking) MessageHelpers.showLongMessage(mContext, mContext.getString(R.string.app_backup) + "\n" + file.getName());

                    RxHelper.runBlocking(mDriveService.uploadFile(file, Uri.parse(String.format("%s%s", backupDir, file.getAbsolutePath().replace(dataDir, "")))));
                }
            }
        };

        if (mIsBlocking) {
            Observable.fromIterable(files)
                    .blockingSubscribe(backupConsumer);
        } else {
            mBackupAction = Observable.fromIterable(files)
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io()) // run subscribe on separate thread
                    .subscribe(backupConsumer, error -> MessageHelpers.showLongMessage(mContext, error.getMessage()));
        }
    }

    private void startBackup2(String backupDir, String dataDir) {
        File source = new File(dataDir);
        File zipFile = new File(mContext.getCacheDir(), BACKUP_NAME);
        ZipHelper.zipFolder(source, zipFile, mBackupNames);

        Observable<Void> uploadFile = mDriveService.uploadFile(zipFile, Uri.parse(String.format("%s/%s", backupDir, BACKUP_NAME)));

        if (mIsBlocking) {
            RxHelper.runBlocking(uploadFile);
        } else {
            MessageHelpers.showLongMessage(mContext, mContext.getString(R.string.app_backup));
            mBackupAction = uploadFile
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            unused -> {},
                            error -> MessageHelpers.showLongMessage(mContext, error.getMessage()),
                            () -> MessageHelpers.showMessage(mContext, R.string.msg_done)
                    );
        }
    }

    private void startRestoreConfirm() {
        AppDialogUtil.showConfirmationDialog(mContext, mContext.getString(R.string.app_restore), this::startRestore);
    }

    private void startRestore() {
        startRestore2(getBackupDir(), mDataDir,
                () -> startRestore2(getAltBackupDir(), mDataDir,
                        () -> startRestore(getBackupDir(), mDataDir,
                                () -> startRestore(getAltBackupDir(), mDataDir, null))));
    }

    private void startRestore(String backupDir, String dataDir, Runnable onError) {
        mRestoreAction = mDriveService.getList(Uri.parse(backupDir))
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io()) // run subscribe on separate thread
                .subscribe(names -> {
                    // remove old data
                    FileHelpers.delete(dataDir);

                    for (String name : names) {
                        if (checkFileName(name)) {
                            MessageHelpers.showLongMessage(mContext, mContext.getString(R.string.app_restore) + "\n" + name);

                            mDriveService.getFile(Uri.parse(String.format("%s/%s", backupDir, name)))
                                    .blockingSubscribe(inputStream -> FileHelpers.copy(inputStream, new File(dataDir, fixAltPackageName(name))));
                        }
                    }
                    
                    Utils.restartTheApp(mContext);
                }, error -> {
                    if (onError != null)
                        onError.run();
                    else MessageHelpers.showLongMessage(mContext, R.string.nothing_found);
                });
    }

    private void startRestore2(String backupDir, String dataDir, Runnable onError) {
        MessageHelpers.showLongMessage(mContext, mContext.getString(R.string.app_restore));
        mRestoreAction = mDriveService.getFile(Uri.parse(String.format("%s/%s", backupDir, BACKUP_NAME)))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(inputStream -> {
                    File zipFile = new File(mContext.getCacheDir(), BACKUP_NAME);
                    FileHelpers.copy(inputStream, zipFile);

                    File out = new File(dataDir);
                    // remove old data
                    FileHelpers.delete(out);
                    ZipHelper.unzipToFolder(zipFile, out);
                    fixFileNames(out);

                    Utils.restartTheApp(mContext);
                }, error -> {
                    if (onError != null)
                        onError.run();
                    else MessageHelpers.showLongMessage(mContext, R.string.nothing_found);
                }, () -> MessageHelpers.showMessage(mContext, R.string.msg_done));
    }

    private void logIn(Runnable onDone) {
        GoogleSignInPresenter.instance(mContext).start(onDone);
    }

    private boolean checkFileName(String name) {
        return Helpers.endsWithAny(name, mBackupNames);
    }

    private String fixAltPackageName(String name) {
        String altPackageName = getAltPackageName();
        return name.replace(altPackageName, mContext.getPackageName());
    }

    private String getAltPackageName() {
        String[] altPackages = new String[] {"com.liskovsoft.smarttubetv.beta", "com.teamsmart.videomanager.tv"};
        return mContext.getPackageName().equals(altPackages[0]) ? altPackages[1] : altPackages[0];
    }

    private String getDeviceSuffix() {
        return mGeneralData.isDeviceSpecificBackupEnabled() ? "_" + Build.MODEL.replace(" ", "_") : "";
    }

    private String getAltBackupDir() {
        String backupDir = getBackupDir();
        String altPackageName = getAltPackageName();
        return backupDir.replace(mContext.getPackageName(), altPackageName);
    }

    public String getBackupDir() {
        return mBackupDir + getDeviceSuffix();
    }

    /**
     * Fix file names from other app versions
     */
    private void fixFileNames(File dataDir) {
        Collection<File> files = FileHelpers.listFileTree(dataDir);

        String suffix = "_preferences.xml";
        String targetName = mContext.getPackageName() + suffix;

        for (File file : files) {
            if (file.getName().endsWith(suffix) && !file.getName().endsWith(targetName)) {
                FileHelpers.copy(file, new File(file.getParentFile(), targetName));
                FileHelpers.delete(file);
            }
        }
    }
}
