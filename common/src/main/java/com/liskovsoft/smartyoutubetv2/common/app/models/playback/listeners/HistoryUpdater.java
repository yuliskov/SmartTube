package com.liskovsoft.smartyoutubetv2.common.app.models.playback.listeners;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;

public class HistoryUpdater extends PlayerEventListenerHelper {
    public HistoryUpdater() {
        
    }

    @Override
    public void onStart(Video item) {
        // save history
    }

    @Override
    public void onSuggestionItemClicked(Video item) {
        // save history
    }
}
