package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.Handler;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.helpers.PermissionHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.HiddenPrefs;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class BackupAndRestoreManager implements MotherActivity.OnPermissions {
    private static final String TAG = BackupAndRestoreManager.class.getSimpleName();
    private static final String BACKUP_DIR_NAME = "Backup";
    private final Context mContext;
    private static final String SHARED_PREFS_SUBDIR = "shared_prefs";
    private final List<File> mDataDirs;
    private final List<File> mBackupDirs;
    private Runnable mPendingHandler;
    private String mBackupName;

    public interface OnBackupNames {
        void onBackupNames(List<String> backupNames);
    }

    public BackupAndRestoreManager(Context context) {
        mContext = context;
        mDataDirs = new ArrayList<>();
        mDataDirs.add(new File(mContext.getApplicationInfo().dataDir, SHARED_PREFS_SUBDIR));

        mBackupDirs = new ArrayList<>();
        mBackupDirs.add(new File(FileHelpers.getBackupDir(mContext), BACKUP_DIR_NAME));
        //mBackupDirs.add(new File(FileHelpers.getExternalFilesDir(mContext), BACKUP_DIR_NAME)); // isn't used at a moment
        // Fallback dir: Stable (in case app installed from scratch)
        mBackupDirs.add(new File(new File(Environment.getExternalStorageDirectory(), "data/com.teamsmart.videomanager.tv"), BACKUP_DIR_NAME));
        // Fallback dir: Beta (in case app installed from scratch)
        mBackupDirs.add(new File(new File(Environment.getExternalStorageDirectory(), "data/com.liskovsoft.smarttubetv.beta"), BACKUP_DIR_NAME));
    }

    public void checkPermAndRestore() {
        checkPermAndRestore(null);
    }

    public void checkPermAndRestore(String name) {
        mBackupName = name;

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
                File destination = new File(currentBackup, dataDir.getName());
                FileHelpers.copy(dataDir, destination);

                // Don't store unique id
                FileHelpers.delete(new File(destination, HiddenPrefs.SHARED_PREFERENCES_NAME + ".xml"));
            }
        }
    }

    private void restoreData() {
        Log.d(TAG, "App just updated. Restoring data...");

        File currentBackup = getBackupCheck();

        if (FileHelpers.isEmpty(currentBackup)) {
            Log.d(TAG, "Oops. Backup folder is empty.");
            MessageHelpers.showLongMessage(mContext, "Oops. Backup folder is empty.");
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
                fixFileNames(dataDir);
            }
        }

        MessageHelpers.showMessage(mContext, R.string.msg_done);

        // To apply settings we need to kill the app
        new Handler(mContext.getMainLooper()).postDelayed(() -> Utils.restartTheApp(mContext), 1_000);
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

    private void verifyStoragePermissionsAndReturn() {
        if (mContext instanceof MotherActivity) {
            ((MotherActivity) mContext).addOnPermissions(this);

            PermissionHelpers.verifyStoragePermissions(mContext);
        }
    }

    private File getBackup() {
        File currentBackup = null;

        for (File backupDir : mBackupDirs) {
            currentBackup = backupDir;
            break;
        }

        return currentBackup;
    }

    private File getBackupCheck() {
        File currentBackup = null;

        for (File backupDir : mBackupDirs) {
            // FileHelpers.isEmpty(backupDir) needs access device storage permission
            if (mBackupName != null && !mBackupName.isEmpty()) {
                backupDir = new File(backupDir.getParentFile(), mBackupName);
            }

            if (backupDir.exists()) {
                currentBackup = backupDir;
                break;
            }
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

    public String getBackupPathCheck() {
        File currentBackup = getBackupCheck();

        return currentBackup != null ? currentBackup.toString() : null;
    }

    public void getBackupNames(OnBackupNames callback) {
        if (FileHelpers.isExternalStorageReadable()) {
            if (PermissionHelpers.hasStoragePermissions(mContext)) {
                callback.onBackupNames(getBackupNames());
            } else {
                mPendingHandler = () -> callback.onBackupNames(getBackupNames());
                verifyStoragePermissionsAndReturn();
            }
        }
    }

    private List<String> getBackupNames() {
        File current = getBackup();

        if (current != null) {
            File parentFile = current.getParentFile();

            if (parentFile == null) {
                return null;
            }

            String[] list = parentFile.list();

            if (list == null) {
                return null;
            }

            List<String> result = new ArrayList<>();

            Arrays.sort(list);

            for (String dirName : list) {
                if (dirName.startsWith(BACKUP_DIR_NAME)) {
                    result.add(dirName);
                }
            }

            return result;
        }

        return null;
    }

    public boolean hasBackup() {
        return getBackupCheck() != null;
    }
}
