package com.liskovsoft.smartyoutubetv2.common.misc;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.Nullable;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;

public class RemoteControlService extends Service {
    private static final String TAG = RemoteControlService.class.getSimpleName();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind: %s", Helpers.toString(intent));

        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: %s", Helpers.toString(intent));

        return super.onStartCommand(intent, flags, startId);
    }
}
