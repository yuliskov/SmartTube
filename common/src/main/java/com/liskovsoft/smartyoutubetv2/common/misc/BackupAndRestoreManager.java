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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BackupAndRestoreManager implements MotherActivity.OnPermissions {
    private static final String TAG = BackupAndRestoreManager.class.getSimpleName();
    private static final String BACKUP_DIR_NAME = "Backup";
    private final Context mContext;
    private static final String SHARED_PREFS_SUBDIR = "shared_prefs";
    private final File mDataDir;
    private final List<File> mBackupDirs;
    private final BackupAndRestoreHelper mHelper;
    private final boolean mForceApi30;
    private final String[] mBackupPatterns = new String[] {
            "yt_service_prefs.xml",
            "com.liskovsoft.appupdatechecker2.preferences.xml",
            "com.liskovsoft.sharedutils.prefs.GlobalPreferences.xml",
            "_preferences.xml" // before _ should be the app package name
    };
    private Runnable mPendingHandler;

    public interface OnBackupNames {
        void onBackupNames(List<String> backupNames);
    }

    public BackupAndRestoreManager(Context context) {
        this(context, false);
    }

    public BackupAndRestoreManager(Context context, boolean forceApi30) {
        mContext = context;
        mForceApi30 = forceApi30;

        mHelper = new BackupAndRestoreHelper(context);

        mDataDir = new File(mContext.getApplicationInfo().dataDir, SHARED_PREFS_SUBDIR);

        mBackupDirs = new ArrayList<>();
    }

    private void initBackupDirs() {
        if (!mBackupDirs.isEmpty()) {
            return;
        }

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

        if (hasAccessOnlyToAppFolders()) {
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

        if (mDataDir.isDirectory() && !FileHelpers.isEmpty(mDataDir)) {
            File destination = new File(currentBackup, mDataDir.getName());
            FileHelpers.copy(mDataDir, destination);

            // Don't store unique id
            FileHelpers.delete(new File(destination, HiddenPrefs.SHARED_PREFERENCES_NAME + ".xml"));
        }

        if (hasAccessOnlyToAppFolders()) {
            mHelper.exportAppMediaFolder();
        }
    }

    private void restoreData(String backupName) {
        Log.d(TAG, "App just updated. Restoring data...");

        File currentBackup = getBackupCheck(backupName);
        File sourceBackupDir = new File(currentBackup, SHARED_PREFS_SUBDIR);

        if (FileHelpers.isEmpty(sourceBackupDir)) {
            Log.d(TAG, "Oops. Backup folder is empty.");
            MessageHelpers.showLongMessage(mContext, "Oops. Backup folder is empty.");
            return;
        }

        if (mDataDir.isDirectory()) {
            // remove old data
            FileHelpers.delete(mDataDir);
        }

        FileHelpers.copy(sourceBackupDir, mDataDir, fileName -> Helpers.endsWithAny(fileName.toString(), mBackupPatterns));
        fixFileNames(mDataDir);

        MessageHelpers.showMessage(mContext, R.string.msg_done);

        // NOTE: Don't restart the app, just kill. The reboot will broke the files.
        // To apply settings we need to kill the app
        new Handler(mContext.getMainLooper()).postDelayed(() -> Runtime.getRuntime().exit(0), 1_000);
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
        initBackupDirs();

        File currentBackup = null;

        for (File backupDir : mBackupDirs) {
            currentBackup = backupDir;
            break;
        }

        return currentBackup;
    }

    private File getBackupCheck(String backupName) {
        initBackupDirs();

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
        initBackupDirs();

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

    public String getBackupRootPath() {
        if (hasAccessOnlyToAppFolders()) {
            return null; // Android 11+: only backup through the file manager (no shared dir)
        }

        return String.format("%s/data", getExternalStorageDirectory());
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
        initBackupDirs();

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

    private boolean hasStoragePermissions(Context context) {
        return hasAccessOnlyToAppFolders() || PermissionHelpers.hasStoragePermissions(context);
    }

    public boolean hasBackup() {
        return getBackupCheck() != null;
    }

    // Android 11+: only backup through the file manager (no shared dir)
    private boolean hasAccessOnlyToAppFolders() {
        return getTargetSdkVersion() > 29 || mForceApi30;
    }

    private int getTargetSdkVersion() {
        return mContext.getApplicationInfo().targetSdkVersion;
    }
}
