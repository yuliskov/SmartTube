package com.liskovsoft.smartyoutubetv2.common.app.models.playback.processors;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;

public class StateRestorer extends PlayerEventListenerHelper {
    private PlayerController mController;

    public StateRestorer(PlayerController controller) {
        mController = controller;
    }

}
