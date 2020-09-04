package com.liskovsoft.smartyoutubetv2.common.app.models.playback;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

public abstract class PlayerEventListenerHelper implements PlayerEventBridge {
    protected PlayerController mController;

    @Override
    public void setController(PlayerController controller) {
        mController = controller;
    }

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

    @Override
    public void onViewCreated() {
        // NOP
    }

    @Override
    public void onViewDestroyed() {
        // NOP
    }

    @Override
    public void onViewPaused() {
        // NOP
    }

    @Override
    public void onViewResumed() {
        // NOP
    }

    @Override
    public void onVideoLoaded() {
        // NOP
    }
}
