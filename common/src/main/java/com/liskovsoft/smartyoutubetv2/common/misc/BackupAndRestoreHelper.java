package com.liskovsoft.smartyoutubetv2.common.misc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.BackupSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity.OnResult;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class BackupAndRestoreHelper implements OnResult {
    private static final int REQ_PICK_FILES = 1001;
    private final Context mContext;
    private Runnable mOnSuccess;

    public BackupAndRestoreHelper(Context context) {
        mContext = context;
    }

    public void exportAppMediaFolder() {
        if (VERSION.SDK_INT < 30) {
            return;
        }

        File mediaDir = getExternalStorageDirectory();
        File dataDir = new File(mediaDir, "data");
        if (!dataDir.exists()) return;

        File zipFile = new File(mediaDir,  "backup_" + mContext.getPackageName() + ".zip");
        zipDirectory(dataDir, zipFile);

        Uri uri = FileProvider.getUriForFile(
                mContext,
                mContext.getPackageName() + ".update_provider",
                zipFile
        );

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        mContext.startActivity(Intent.createChooser(intent, "Send to Total Commander"));
    }

    public void importAppMediaFolder(Runnable onSuccess) {
        if (VERSION.SDK_INT < 30 || onSuccess == null) {
            return;
        }

        mOnSuccess = onSuccess;

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        //intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        ((MotherActivity) mContext).addOnResult(this);

        ((Activity) mContext).startActivityForResult(intent, REQ_PICK_FILES);
    }

    @Override
    public void onResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_PICK_FILES && resultCode == Activity.RESULT_OK) {

            if (data == null) return;

            File mediaDir = getExternalStorageDirectory();
            File dataDir = new File(mediaDir, "data");

            if (!mediaDir.exists()) mediaDir.mkdirs();

            Uri uri = data.getData();
            if (uri == null && data.getClipData() != null) {
                uri = data.getClipData().getItemAt(0).getUri();
            }
            if (uri == null) return;

            File zipFile = new File(mediaDir, "restore.zip");
            copyUriToDir(uri, zipFile);

            // Cleanup previous data
            deleteRecursive(dataDir);
            dataDir.mkdirs();

            unzip(zipFile, mediaDir);

            zipFile.delete();

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
            // Target folder: /Android/media/<package>/data
            File mediaDir = getExternalStorageDirectory();
            File dataDir = new File(mediaDir, "data");

            // Remove old data
            if (dataDir.exists()) deleteRecursive(dataDir);

            // Copy ZIP from URI to temporary file
            File tempZip = new File(mediaDir, "imported_backup.zip");
            copyUriToFile(zipUri, tempZip);

            // Unpack ZIP with data folder
            unzip(tempZip, mediaDir);

            if (FileHelpers.isEmpty(dataDir)) {
                // Seems we've packed the contents of the data dir not data itself
                unzip(tempZip, dataDir);
            }

            // Delete the temporary ZIP
            tempZip.delete();

            BackupSettingsPresenter.instance(mContext).showLocalRestoreDialogApi30();

            //Toast.makeText(mContext, "Backup restored successfully", Toast.LENGTH_SHORT).show();

            // TODO: possibly launch restore dialog
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(mContext, "Failed to restore backup", Toast.LENGTH_SHORT).show();
        }
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

    private void zipDirectory(File sourceDir, File zipFile) {
        try {
            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));
            zipFileRecursive(zos, sourceDir, "data/");
            zos.close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void zipFileRecursive(ZipOutputStream zos, File file, String base) throws Exception {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    zipFileRecursive(zos, child, base + child.getName() + "/");
                }
            }
        } else {
            FileInputStream fis = new FileInputStream(file);
            zos.putNextEntry(new ZipEntry(base.substring(0, base.length() -1))); // strip "/" at the end to mark as file
            byte[] buf = new byte[8192];
            int len;
            while ((len = fis.read(buf)) > 0) zos.write(buf, 0, len);
            fis.close();
            zos.closeEntry();
        }
    }

    private void unzip(File zipFile, File targetRoot) {
        try {
            ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
            ZipEntry entry;
            byte[] buffer = new byte[8192];

            while ((entry = zis.getNextEntry()) != null) {
                File out = new File(targetRoot, entry.getName());
                if (entry.isDirectory()) {
                    out.mkdirs();
                } else {
                    out.getParentFile().mkdirs();
                    FileOutputStream fos = new FileOutputStream(out);
                    int len;
                    while ((len = zis.read(buffer)) > 0) fos.write(buffer, 0, len);
                    fos.close();
                }
                zis.closeEntry();
            }
            zis.close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void deleteRecursive(File f) {
        if (!f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) deleteRecursive(c);
            }
        }
        f.delete();
    }
}
