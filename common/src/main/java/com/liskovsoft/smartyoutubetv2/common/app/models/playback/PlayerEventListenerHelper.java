package com.liskovsoft.smartyoutubetv2.common.app.models.playback;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;

public abstract class PlayerEventListenerHelper implements PlayerEventListener {
    protected PlayerController mController;
    protected FragmentActivity mActivity;

    @Override
    public void setController(PlayerController controller) {
        mController = controller;
        mActivity = ((Fragment) controller).getActivity();
    }

    @Override
    public void openVideo(Video item) {
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
    public boolean onPreviousClicked() {
        // NOP
        return false;
    }

    @Override
    public boolean onNextClicked() {
        // NOP
        return false;
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

    @Override
    public void onRepeatModeClicked(int modeIndex) {
        // NOP
    }

    @Override
    public void onSeek() {
        // NOP
    }

    @Override
    public void onPlayEnd() {
        // NOP
    }

    @Override
    public void onKeyDown(int keyCode) {
        // NOP
    }

    @Override
    public void onRepeatModeChange(int modeIndex) {
        // NOP
    }

    @Override
    public void onHighQualityClicked() {
        // NOP
    }

    @Override
    public void onSubscribeClicked(boolean subscribed) {
        // NOP
    }

    @Override
    public void onThumbsDownClicked(boolean thumbsDown) {
        // NOP
    }

    @Override
    public void onThumbsUpClicked(boolean thumbsUp) {
        // NOP
    }

    @Override
    public void onChannelClicked() {
        // NOP
    }

    @Override
    public void onTrackClicked(OptionItem track) {
        // NOP
    }

    @Override
    public void onTrackChanged(FormatItem track) {
        // NOP
    }
}
