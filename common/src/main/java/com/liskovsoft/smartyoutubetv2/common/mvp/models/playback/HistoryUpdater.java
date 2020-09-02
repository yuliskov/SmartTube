package com.liskovsoft.smartyoutubetv2.common.mvp.models.playback;

import com.liskovsoft.smartyoutubetv2.common.mvp.models.data.Video;

public class HistoryUpdater extends PlayerCommandProcessorHelper {
    private PlayerCommandHandler mCommandHandler;

    @Override
    public void setCommandHandler(PlayerCommandHandler commandHandler) {
        mCommandHandler = commandHandler;
    }

    @Override
    public void onInit(Video item) {
        // save history
    }

    @Override
    public void onSuggestionItemClicked(Video item) {
        // save history
    }
}
