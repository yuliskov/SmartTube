package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.helpers.PermissionHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class BackupAndRestoreManager implements MotherActivity.OnPermissions {
    private static final String TAG = BackupAndRestoreManager.class.getSimpleName();
    private final Context mContext;
    private final List<File> mDataDirs;
    private static final String SHARED_PREFS_SUBDIR = "shared_prefs";
    private final List<File> mBackupDirs;
    private Runnable mPendingHandler;

    public BackupAndRestoreManager(Context context) {
        mContext = context;
        mDataDirs = new ArrayList<>();
        mDataDirs.add(new File(mContext.getApplicationInfo().dataDir, SHARED_PREFS_SUBDIR));

        mBackupDirs = new ArrayList<>();
        mBackupDirs.add(new File(FileHelpers.getBackupDir(mContext), "Backup"));
        mBackupDirs.add(new File(FileHelpers.getExternalFilesDir(mContext), "Backup")); // isn't used at a moment
    }

    public void checkPermAndRestore() {
        if (FileHelpers.isExternalStorageReadable()) {
            if (PermissionHelpers.hasStoragePermissions(mContext)) {
                restoreData();
            } else {
                mPendingHandler = this::restoreData;
                verifyStoragePermissionsAndReturn();
            }
        }
    }

    public void checkPermAndBackup() {
        if (FileHelpers.isExternalStorageWritable()) {
            if (PermissionHelpers.hasStoragePermissions(mContext)) {
                backupData();
            } else {
                mPendingHandler = this::backupData;
                verifyStoragePermissionsAndReturn();
            }
        }
    }

    private void backupData() {
        Log.d(TAG, "App has been updated or installed. Doing data backup...");

        File currentBackup = getBackup();

        if (currentBackup == null) {
            Log.d(TAG, "Oops. Backup location not writable.");
            return;
        }

        // remove old backup
        if (currentBackup.isDirectory()) {
            FileHelpers.delete(currentBackup);
        }

        for (File dataDir : mDataDirs) {
            if (dataDir.isDirectory() && !FileHelpers.isEmpty(dataDir)) {
                FileHelpers.copy(dataDir, new File(currentBackup, dataDir.getName()));
            }
        }
    }

    private void restoreData() {
        Log.d(TAG, "App just updated. Restoring data...");

        File currentBackup = getBackup();

        if (FileHelpers.isEmpty(currentBackup)) {
            Log.d(TAG, "Oops. Backup not exists.");
            return;
        }

        for (File dataDir : mDataDirs) {
            if (dataDir.isDirectory()) {
                // remove old data
                FileHelpers.delete(dataDir);
            }

            File sourceBackupDir = new File(currentBackup, dataDir.getName());

            if (sourceBackupDir.exists() && !FileHelpers.isEmpty(sourceBackupDir)) {
                FileHelpers.copy(sourceBackupDir, dataDir);
            }
        }

        // To apply settings we need to kill the app
        new Handler(mContext.getMainLooper()).postDelayed(() -> ViewManager.instance(mContext).forceFinishTheApp(), 1_000);
    }

    private void verifyStoragePermissionsAndReturn() {
        PermissionHelpers.verifyStoragePermissions(mContext);
    }

    private File getBackup() {
        File currentBackup = null;

        for (File backupDir : mBackupDirs) {
            currentBackup = backupDir;
            break;
        }

        return currentBackup;
    }

    @Override
    public void onPermissions(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PermissionHelpers.REQUEST_EXTERNAL_STORAGE) {
            if (grantResults.length >= 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "REQUEST_EXTERNAL_STORAGE permission has been granted");

                if (mPendingHandler != null) {
                    mPendingHandler.run();
                    mPendingHandler = null;
                }
            }
        }
    }

    public String getBackupPath() {
        File currentBackup = getBackup();

        return currentBackup != null ? currentBackup.toString() : null;
    }
}
