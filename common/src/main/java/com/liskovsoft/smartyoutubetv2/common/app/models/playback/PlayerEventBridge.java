package com.liskovsoft.smartyoutubetv2.common.app.models.playback;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

public interface PlayerEventBridge extends PlayerEventListener {
    void onStart(Video item);
    void setController(PlayerController controller);
}
