package com.liskovsoft.smartyoutubetv2.common.misc;

import android.app.IntentService;
import android.content.Intent;
import androidx.annotation.Nullable;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;

public class RemoteControlService extends IntentService {
    private static final String TAG = RemoteControlService.class.getSimpleName();

    public RemoteControlService() {
        super(RemoteControlService.class.getSimpleName());
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        // NOP
        Log.d(TAG, "onHandleIntent: " + Helpers.toString(intent));
    }
}
