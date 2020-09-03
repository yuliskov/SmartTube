package com.liskovsoft.smartyoutubetv2.common.mvp.models.playback;

import com.liskovsoft.smartyoutubetv2.common.mvp.models.data.Video;

public abstract class PlayerCommandProcessorHelper implements PlayerEventBridge {
    @Override
    public void onInit(Video item) {
        // NOP
    }

    @Override
    public void onSuggestionItemClicked(Video item) {
        // NOP
    }

    @Override
    public void onSuggestionItemLongClicked(Video item) {
        // NOP
    }

    @Override
    public void onPrevious() {
        // NOP
    }

    @Override
    public void onNext() {
        // NOP
    }
}
