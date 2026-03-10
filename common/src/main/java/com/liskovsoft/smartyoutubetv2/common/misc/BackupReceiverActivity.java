package com.liskovsoft.smartyoutubetv2.common.misc;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build.VERSION;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.liskovsoft.sharedutils.helpers.PermissionHelpers;

public class BackupReceiverActivity extends Activity {
    private BackupAndRestoreHelper mRestoreHelper;
    private Runnable mPendingHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mRestoreHelper = new BackupAndRestoreHelper(this);

        // Android 11+ (API 30+) removed the need for storage permission
        // when accessing files via content:// URIs. Apps are granted temporary
        // access to the URI by the sender, so you can read the file without
        // READ_EXTERNAL_STORAGE.
        if (PermissionHelpers.hasStoragePermissions(this) || VERSION.SDK_INT > 29) {
            restoreData();
            finish();
        } else {
            mPendingHandler = this::restoreData;
            PermissionHelpers.verifyStoragePermissions(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        mRestoreHelper.handleIncomingZip(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PermissionHelpers.REQUEST_EXTERNAL_STORAGE) {
            if (grantResults.length >= 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (mPendingHandler != null) {
                    mPendingHandler.run();
                    mPendingHandler = null;
                }
            }
            finish();
        }
    }

    private void restoreData() {
        mRestoreHelper.handleIncomingZip(getIntent());
    }
}
