package com.liskovsoft.smartyoutubetv2.common.misc;

import android.app.IntentService;
import android.content.Intent;
import androidx.annotation.Nullable;

public class RemoteControlService extends IntentService {
    public RemoteControlService() {
        super(RemoteControlService.class.getSimpleName());
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        // NOP
    }
}
