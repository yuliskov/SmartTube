package com.liskovsoft.smartyoutubetv2.common.misc;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;

import com.liskovsoft.googleapi.service.DriveService;
import com.liskovsoft.googleapi.oauth2.impl.GoogleSignInService;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.GoogleSignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class GDriveBackupManager {
    @SuppressLint("StaticFieldLeak")
    private static GDriveBackupManager sInstance;
    private final Context mContext;
    private static final String FILES_SUBDIR = "files";
    private static final String SHARED_PREFS_SUBDIR = "shared_prefs";
    private static final String BACKUP_NAME = "backup.zip";
    private final GoogleSignInService mSignInService;
    private final String mRootDir;
    private final String mSharedPrefs;
    private final String mFilesDir;
    private final String[] mDataDirs;
    private final String mBackupDir;
    private final String mRootBackupDir;
    private final GeneralData mGeneralData;
    private Disposable mBackupAction;
    private Disposable mRestoreAction;
    private boolean mIsBlocking;

    private GDriveBackupManager(Context context) {
        mContext = context;
        mGeneralData = GeneralData.instance(context);
        mRootDir = mContext.getApplicationInfo().dataDir;
        mSharedPrefs = String.format("%s/%s", mRootDir, SHARED_PREFS_SUBDIR);
        mFilesDir = String.format("%s/%s", mRootDir, FILES_SUBDIR);
        mDataDirs = new String[] { mSharedPrefs, mFilesDir };
        mBackupDir = String.format("SmartTubeBackup/%s", context.getPackageName());
        mRootBackupDir = "SmartTubeBackup";
        mSignInService = GoogleSignInService.instance();
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
            AppDialogUtil.showConfirmationDialog(mContext, mContext.getString(R.string.app_backup), this::startBackupWrapper);
        } else {
            startBackupWrapper();
        }
    }

    private void startBackupWrapper() {
        String backupDir = getBackupDir();
        startBackup(backupDir);
    }

    private void startBackup(String backupDir) {
        // First, copy to the backup dir
        File source = new File(FileHelpers.getExternalMediaDirectory(mContext), "data");
        new BackupAndRestoreManager(mContext).backupToDir(source);
        File zipFile = new File(mContext.getCacheDir(), BACKUP_NAME);
        ZipHelper.zipFolder(source, zipFile, null);

        Observable<Void> uploadFile = DriveService.uploadFile(zipFile, Uri.parse(String.format("%s/%s", backupDir, BACKUP_NAME)));

        if (mIsBlocking) {
            RxHelper.runBlocking(uploadFile);
        } else {
            MessageHelpers.showLongMessage(mContext, mContext.getString(R.string.app_backup));
            mBackupAction = uploadFile
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            unused -> {},
                            error -> {
                                MessageHelpers.showLongMessage(mContext, error.getMessage());
                                if (Helpers.startsWith(error.getMessage(), "AuthError")) {
                                    logIn(this::startBackupConfirm); // auth data outdated (AuthError: invalid_grant)
                                }
                            },
                            () -> MessageHelpers.showMessage(mContext, R.string.msg_done)
                    );
        }
    }

    private void startRestoreConfirm() {
        //AppDialogUtil.showConfirmationDialog(mContext, mContext.getString(R.string.app_restore), this::startRestoreWrapper);
        showRestoreChooserDialog();
    }

    private void startRestoreOld(String backupDir, Runnable onError) {
        mRestoreAction = DriveService.getFileList(Uri.parse(backupDir))
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io()) // run subscribe on separate thread
                .subscribe(names -> {
                    if (names == null)
                        return;

                    // remove old data
                    for (String dataDir : mDataDirs) {
                        FileHelpers.delete(dataDir);
                    }

                    for (String name : names) {
                        if (name == null)
                            continue;

                        MessageHelpers.showLongMessage(mContext, mContext.getString(R.string.app_restore) + "\n" + name);

                        DriveService.getFile(Uri.parse(String.format("%s/%s", backupDir, name)))
                                .blockingSubscribe(inputStream -> FileHelpers.copy(inputStream, new File(mSharedPrefs, fixAltPackageName(name))));
                    }

                    // NOTE: Don't restart the app, just kill. The reboot will broke the files.
                    // To apply settings we need to kill the app
                    new Handler(mContext.getMainLooper()).postDelayed(() -> Runtime.getRuntime().exit(0), 1_000);
                }, error -> {
                    if (onError != null)
                        onError.run();
                    else MessageHelpers.showLongMessage(mContext, error.getMessage());
                });
    }

    private void startRestore(String backupDir, Runnable onError) {
        MessageHelpers.showLongMessage(mContext, mContext.getString(R.string.app_restore));
        mRestoreAction = DriveService.getFile(Uri.parse(String.format("%s/%s", backupDir, BACKUP_NAME)))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(inputStream -> {
                    File zipFile = new File(mContext.getCacheDir(), BACKUP_NAME);
                    FileHelpers.copy(inputStream, zipFile);

                    File sharedPrefs = new File(mSharedPrefs);

                    // remove old data
                    for (String dataDir : mDataDirs) {
                        FileHelpers.delete(dataDir);
                    }

                    if (ZipHelper.hasDirectories(zipFile)) { // the new format, dirs: /files /share_prefs
                        ZipHelper.unzipToFolder(zipFile, new File(mRootDir));
                    } else { // the old format: only xml files
                        ZipHelper.unzipToFolder(zipFile, sharedPrefs);
                    }
                    fixFileNames(sharedPrefs);

                    // NOTE: Don't restart the app, just kill. The reboot will broke the files.
                    // To apply settings we need to kill the app
                    new Handler(mContext.getMainLooper()).postDelayed(() -> Runtime.getRuntime().exit(0), 1_000);
                }, error -> {
                    if (onError != null)
                        onError.run();
                    else MessageHelpers.showLongMessage(mContext, error.getMessage());
                }, () -> MessageHelpers.showMessage(mContext, R.string.msg_done));
    }

    private void logIn(Runnable onDone) {
        GoogleSignInPresenter.instance(mContext).start(onDone);
    }

    private String fixAltPackageName(String name) {
        String altPackageName = getAltPackageName();
        return name.replace(altPackageName, mContext.getPackageName());
    }

    private String getAltPackageName() {
        String[] altPackages = Utils.KNOWN_PACKAGES;
        // TODO: don't hard code ids. show all existed.
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

    private void showRestoreChooserDialog() {
        mRestoreAction = DriveService.getFolderList(Uri.parse(mRootBackupDir))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread()) // run subscribe on separate thread
                .subscribe(
                        this::showLocalRestoreDialog,
                        error -> {
                            MessageHelpers.showLongMessage(mContext, error.getMessage());
                            if (Helpers.startsWith(error.getMessage(), "AuthError")) {
                                logIn(this::startRestoreConfirm); // auth data outdated (AuthError: invalid_grant)
                            }
                        }
                );
    }

    private void showLocalRestoreDialog(List<String> backups) {
        if (backups != null && !backups.isEmpty()) {
            showLocalRestoreSelectorDialog(backups);
        } else {
            MessageHelpers.showLongMessage(mContext, R.string.nothing_found);
        }
    }

    private void showLocalRestoreSelectorDialog(List<String> backups) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(mContext);
        List<OptionItem> options = new ArrayList<>();

        for (String name : backups) {
            options.add(UiOptionItem.from(name, optionItem -> {
                AppDialogUtil.showConfirmationDialog(mContext, mContext.getString(R.string.app_restore), () -> {
                    String backupDir = String.format("%s/%s", mRootBackupDir, name);
                    startRestore(backupDir, () -> startRestoreOld(backupDir, null));
                });
            }));
        }

        dialog.appendStringsCategory(mContext.getString(R.string.app_restore), options);
        dialog.showDialog();
    }
}
