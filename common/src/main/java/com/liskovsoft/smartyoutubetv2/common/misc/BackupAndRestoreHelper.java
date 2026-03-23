package com.liskovsoft.smartyoutubetv2.common.misc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.provider.OpenableColumns;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.BackupSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity.OnResult;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class BackupAndRestoreHelper implements OnResult {
    private static final int REQ_PICK_FILES = 1001;
    private final Context mContext;
    private Runnable mOnSuccess;
    private final String[] mPreferredFileManagers = {
            "com.ghisler.android.TotalCommander",
            "com.lonelycatgames.Xplore",
            "com.alphainventor.filemanager",
            "pl.solidexplorer2"
    };

    public BackupAndRestoreHelper(Context context) {
        mContext = context;
    }

    public void exportAppMediaFolder() {
        File mediaDir = FileHelpers.getExternalMediaDirectory(mContext);
        File dataDir = new File(mediaDir, "data");
        if (!dataDir.exists() || FileHelpers.isEmpty(dataDir)) return;

        File zipFile = new File(mediaDir,  "backup_" + mContext.getPackageName() + ".zip");
        ZipHelper2.zipDirectory(dataDir, zipFile);

        //if (VERSION.SDK_INT >= 29) {
        //    MFile file = new MFile(mContext, zipFile.getName());
        //    file.copyFrom(zipFile);
        //    //file.getStoredName();
        //}

        Uri uri = FileProvider.getUriForFile(
                mContext,
                mContext.getPackageName() + ".update_provider",
                zipFile
        );

        try {
            openFileManager(uri);
        } catch (Exception e) {
            // Activity launch may fail if called from background (e.g. WorkManager)
            e.printStackTrace();
        }
    }

    private void openFileManager(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        //intent.setType("application/zip");
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PackageManager pm = mContext.getPackageManager();

        for (String pkg : mPreferredFileManagers) {
            Intent targeted = new Intent(intent);
            targeted.setPackage(pkg);
            if (targeted.resolveActivity(pm) != null) {
                mContext.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                mContext.startActivity(targeted);
                return;
            }
        }

        mContext.startActivity(Intent.createChooser(intent, mContext.getString(R.string.app_backup)));
    }

    /**
     * NOTE: The file picker relies on apps that support the Storage Access Framework (SAF).
     * At the moment, no known third-party file manager properly supports selecting ZIP
     * archives through this API, so the backup file may not appear in the picker.
     */
    public void importAppMediaFolder(Runnable onSuccess) {
        if (VERSION.SDK_INT < 19 || onSuccess == null) {
            return;
        }

        mOnSuccess = onSuccess;

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        //intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        //intent.addCategory(Intent.CATEGORY_OPENABLE);
        //intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
        //        "application/zip",
        //        "application/x-zip-compressed"
        //});

        ((MotherActivity) mContext).addOnResult(this);

        ((Activity) mContext).startActivityForResult(intent, REQ_PICK_FILES);
    }

    @Override
    public void onResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_PICK_FILES && resultCode == Activity.RESULT_OK) {

            if (data == null) return;

            File mediaDir = FileHelpers.getExternalMediaDirectory(mContext);

            Uri uri = data.getData();
            if (uri == null && data.getClipData() != null) {
                uri = data.getClipData().getItemAt(0).getUri();
            }
            if (uri == null) return;

            File zipFile = new File(mediaDir, "restore.zip");
            copyUriToDir(uri, zipFile);

            unpackTempZip(zipFile);

            mOnSuccess.run();
        }
    }

    public void handleIncomingZip(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;

        Uri zipUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (zipUri == null) {
            Toast.makeText(mContext, "No ZIP received", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File mediaDir = FileHelpers.getExternalMediaDirectory(mContext);

            // Copy ZIP from URI to temporary file
            File tempZip = new File(mediaDir, "imported_backup.zip");
            copyUriToFile(zipUri, tempZip);

            unpackTempZip(tempZip);

            BackupSettingsPresenter.instance(mContext).showLocalRestoreDialogApi30();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(mContext, "Failed to restore backup", Toast.LENGTH_SHORT).show();
        }
    }

    public void unpackTempZip(File tempZip) {
        if (!tempZip.exists()) {
            return;
        }

        // Target folder: /Android/media/<package>/data
        File mediaDir = FileHelpers.getExternalMediaDirectory(mContext);
        File dataDir = new File(mediaDir, "data");

        // Remove old data
        if (dataDir.exists()) FileHelpers.delete(dataDir);

        if (ZipHelper2.hasRootDir(tempZip, "data")) {
            // Unpack ZIP with data folder
            ZipHelper2.unzip(tempZip, mediaDir);
        } else {
            // Seems we've packed the contents of the data dir not data itself
            ZipHelper2.unzip(tempZip, dataDir);
        }

        // Delete the temporary ZIP
        tempZip.delete();
    }

    private void copyUriToDir(Uri uri, File targetDir) {
        try {
            String fileName = getFileName(uri);
            if (fileName == null) fileName = "imported_" + System.currentTimeMillis();

            File outFile = new File(targetDir, fileName);

            InputStream in = mContext.getContentResolver().openInputStream(uri);
            OutputStream out = new FileOutputStream(outFile);

            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }

            in.close();
            out.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void copyUriToFile(Uri uri, File outFile) {
        try {
            InputStream in = mContext.getContentResolver().openInputStream(uri);
            OutputStream out = new FileOutputStream(outFile);

            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }

            in.close();
            out.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getFileName(Uri uri) {
        Cursor cursor = mContext.getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            cursor.moveToFirst();
            String name = cursor.getString(nameIndex);
            cursor.close();
            return name;
        }
        return null;
    }
}
