package com.liskovsoft.smartyoutubetv2.common.misc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class BackupReceiverActivity extends Activity {
    private BackupAndRestoreHelper mRestoreHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mRestoreHelper = new BackupAndRestoreHelper(this);

        mRestoreHelper.handleIncomingZip(getIntent());
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        mRestoreHelper.handleIncomingZip(intent);
    }
}
