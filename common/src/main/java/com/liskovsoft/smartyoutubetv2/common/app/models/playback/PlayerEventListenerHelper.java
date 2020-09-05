package com.liskovsoft.smartyoutubetv2.common.app.models.playback;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

public abstract class PlayerEventListenerHelper implements PlayerEventBridge {
    protected PlayerController mController;
    protected FragmentActivity mActivity;

    @Override
    public void setController(PlayerController controller) {
        mController = controller;
        mActivity = ((Fragment) controller).getActivity();
    }

    @Override
    public void setFirstVideo(Video item) {
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
    public void onPreviousClicked() {
        // NOP
    }

    @Override
    public void onNextClicked() {
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
    public void onVideoLoaded(Video item) {
        // NOP
    }

    @Override
    public void onEngineInitialized() {
        // NOP
    }

    @Override
    public void onEngineReleased() {
        // NOP
    }

    @Override
    public void onPlay() {
        // NOP
    }

    @Override
    public void onPause() {
        // NOP
    }

    @Override
    public void onPlayClicked() {
        // NOP
    }

    @Override
    public void onPauseClicked() {
        // NOP
    }
}
