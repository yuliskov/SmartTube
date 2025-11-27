package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build.VERSION;
import android.os.Environment;
import android.os.Handler;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.helpers.PermissionHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.prefs.HiddenPrefs;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.io.File;
import java.util.ArrayList;
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

    public interface OnBackupNames {
        void onBackupNames(List<String> backupNames);
    }

    public BackupAndRestoreManager(Context context) {
        mContext = context;

        mDataDirs = new ArrayList<>();
        mDataDirs.add(new File(mContext.getApplicationInfo().dataDir, SHARED_PREFS_SUBDIR));

        mBackupDirs = new ArrayList<>();

        initBackupDirs();
    }

    private void initBackupDirs() {
        File externalDir = getExternalStorageDirectory();
        // Main backup dir
        mBackupDirs.add(createBackupDir(new File(externalDir, String.format("data/%s", mContext.getPackageName()))));

        File dataDir = new File(externalDir, "data");

        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        File[] appDirs = dataDir.listFiles();

        if (appDirs == null) {
            return;
        }

        // Fallback dirs: in case multiple app flavors installed
        for (File appDir : appDirs) {
            File backupDir = createBackupDir(appDir);
            if (!mBackupDirs.contains(backupDir)) {
                mBackupDirs.add(backupDir);
            }
        }
    }

    private File createBackupDir(File appDir) {
        return new File(appDir, BACKUP_DIR_NAME);
    }

    @SuppressWarnings("deprecation")
    private File getExternalStorageDirectory() {
        File result;

        if (VERSION.SDK_INT > 29) {
            result = mContext.getExternalMediaDirs()[0];

            if (!result.exists()) {
                result.mkdirs();
            }
        } else {
            result = Environment.getExternalStorageDirectory();
        }

        return result;
    }

    public void checkPermAndRestore() {
        List<String> backupNames = getBackupNames();

        if (!backupNames.isEmpty()) {
            checkPermAndRestore(backupNames.get(0));
        }
    }

    public void checkPermAndRestore(String backupName) {
        if (backupName == null) {
            return;
        }

        if (FileHelpers.isExternalStorageReadable()) {
            if (hasStoragePermissions(mContext)) {
                restoreData(backupName);
            } else {
                mPendingHandler = () -> restoreData(backupName);
                verifyStoragePermissionsAndReturn();
            }
        }
    }

    public void checkPermAndBackup() {
        if (FileHelpers.isExternalStorageWritable()) {
            if (hasStoragePermissions(mContext)) {
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

    private void restoreData(String backupName) {
        Log.d(TAG, "App just updated. Restoring data...");

        File currentBackup = getBackupCheck(backupName);

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

    private File getBackupCheck(String backupName) {
        File currentBackup = null;

        for (File backupDir : mBackupDirs) {
            File parentFile = backupDir.getParentFile(); // backupDir: /data/<app_id>/Backup

            if (parentFile == null) {
                continue;
            }

            if (backupDir.exists() && Helpers.equals(parentFile.getName(), backupName)) {
                currentBackup = backupDir;
                break;
            }
        }

        return currentBackup;
    }

    private File getBackupCheck() {
        for (File backupDir : mBackupDirs) {
            if (backupDir.exists()) {
                return backupDir.getParentFile();
            }
        }

        return null;
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
            if (hasStoragePermissions(mContext)) {
                callback.onBackupNames(getBackupNames());
            } else {
                mPendingHandler = () -> callback.onBackupNames(getBackupNames());
                verifyStoragePermissionsAndReturn();
            }
        }
    }

    private List<String> getBackupNames() {
        List<String> names = new ArrayList<>();

        for (File backupDir : mBackupDirs) {
            File parentFile = backupDir.getParentFile();

            if (parentFile == null) {
                continue;
            }

            if (backupDir.exists()) {
                names.add(parentFile.getName());
            }
        }

        return names;
    }

    private static boolean hasStoragePermissions(Context context) {
        return VERSION.SDK_INT > 29 || PermissionHelpers.hasStoragePermissions(context);
    }

    public boolean hasBackup() {
        return getBackupCheck() != null;
    }
}
