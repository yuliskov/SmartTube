package com.liskovsoft.smartyoutubetv2.common.app.models.playback;

import android.app.Activity;
import androidx.fragment.app.Fragment;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerHandlerEventListener;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;

public abstract class PlayerEventListenerHelper implements PlayerHandlerEventListener {
    protected PlaybackController mController;
    protected Activity mMainActivity;
    protected Activity mParentActivity;

    @Override
    public void onController(PlaybackController controller) {
        mController = controller;
    }

    @Override
    public void onMainActivity(Activity activity) {
        mMainActivity = activity;
    }

    @Override
    public void onParentActivity(Activity activity) {
        mParentActivity = activity;
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
    public void onTrackClicked(FormatItem track) {
        // NOP
    }

    @Override
    public void onClosedCaptionsClicked() {
        // NOP
    }

    @Override
    public void onTrackChanged(FormatItem track) {
        // NOP
    }
}
