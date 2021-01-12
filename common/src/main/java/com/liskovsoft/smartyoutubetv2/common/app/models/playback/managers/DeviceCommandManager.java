package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import com.liskovsoft.mediaserviceinterfaces.CommandManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.prefs.DeviceLinkData;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;

public class DeviceCommandManager extends PlayerEventListenerHelper {
    private final CommandManager mDeviceCommandManager;
    private final DeviceLinkData mDeviceLinkData;

    public DeviceCommandManager() {
        MediaService mediaService = YouTubeMediaService.instance();
        mDeviceCommandManager = mediaService.getCommandManager();
        mDeviceLinkData = DeviceLinkData.instance(null);
        tryListening();
    }

    private void tryListening() {
        if (mDeviceLinkData.isDeviceLinkEnabled()) {
            startListening();
        } else {
            stopListening();
        }
    }

    private void startListening() {

    }

    private void stopListening() {

    }
}
