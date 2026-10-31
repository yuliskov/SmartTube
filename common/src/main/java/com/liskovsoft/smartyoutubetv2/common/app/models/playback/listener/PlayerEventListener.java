package com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerManager;
import com.liskovsoft.smartyoutubetv2.common.misc.TickleManager.TickleListener;

public interface PlayerEventListener extends PlayerUiEventListener, PlayerEngineEventListener, ViewEventListener, TickleListener {
    void onNewVideo(Video item);
    void onMetadata(MediaItemMetadata metadata);
    /**
     * Called after creation of {@link PlayerManager}
     */
    void onInit();
    void onFinish();
}
