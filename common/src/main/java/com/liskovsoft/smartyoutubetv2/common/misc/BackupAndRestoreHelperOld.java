package com.liskovsoft.smartyoutubetv2.common.misc;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Environment;
import android.provider.OpenableColumns;

import androidx.core.content.FileProvider;

import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity.OnResult;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class BackupAndRestoreHelperOld implements OnResult {
    private static final int REQ_PICK_FILES = 1001;
    private final Context mContext;

    public BackupAndRestoreHelperOld(Context context) {
        mContext = context;
    }

    public void exportAppMediaFolder(Context context) {
        if (VERSION.SDK_INT < 30) {
            return;
        }

        File mediaDir = getExternalStorageDirectory();
        if (mediaDir == null || !mediaDir.exists()) return;

        // TODO: create zip file and send only one file zip
        File[] files = mediaDir.listFiles();
        if (files == null || files.length == 0) return;

        ArrayList<Uri> uris = new ArrayList<>();

        for (File file : files) {
            if (file.isFile()) {
                Uri uri = FileProvider.getUriForFile(
                        context,
                        context.getPackageName() + ".update_provider",
                        file
                );
                uris.add(uri);
            }
        }

        if (uris.isEmpty()) return;

        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setType("*/*");
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        context.startActivity(
                Intent.createChooser(intent, "Send to Total Commander")
        );
    }

    public void importAppMediaFolder(Context context) {
        if (VERSION.SDK_INT < 30) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        ((MotherActivity) mContext).addOnResult(this);

        ((Activity) context).startActivityForResult(intent, REQ_PICK_FILES);
    }

    @Override
    public void onResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_PICK_FILES && resultCode == Activity.RESULT_OK) {

            if (data == null) return;

            File targetDir = getExternalStorageDirectory();
            if (!targetDir.exists()) targetDir.mkdirs();

            // Несколько файлов (ClipData)
            if (data.getClipData() != null) {
                ClipData clip = data.getClipData();
                for (int i = 0; i < clip.getItemCount(); i++) {
                    Uri uri = clip.getItemAt(i).getUri();
                    copyUriToFile(mContext, uri, targetDir);
                }
            }
            // Один файл
            else if (data.getData() != null) {
                Uri uri = data.getData();
                copyUriToFile(mContext, uri, targetDir);
            }
        }
    }

    private static void copyUriToFile(Context context, Uri uri, File targetDir) {
        try {
            String fileName = getFileName(context, uri);
            if (fileName == null) fileName = "imported_" + System.currentTimeMillis();

            File outFile = new File(targetDir, fileName);

            InputStream in = context.getContentResolver().openInputStream(uri);
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

    private static String getFileName(Context context, Uri uri) {
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
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
}
