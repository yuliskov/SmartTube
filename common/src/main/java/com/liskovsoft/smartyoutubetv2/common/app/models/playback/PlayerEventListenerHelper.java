package com.liskovsoft.smartyoutubetv2.common.app.models.playback;

import android.app.Activity;
import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;

public abstract class PlayerEventListenerHelper implements PlayerEventListener {
    private MainPlayerController mMainController;
    private Context mContext;

    public void setMainController(MainPlayerController mainController) {
        mMainController = mainController;
    }

    public void setAltContext(Context context) {
        mContext = context;
    }

    public MainPlayerController getMainController() {
        return mMainController;
    }

    public PlayerManager getPlayer() {
        return mMainController != null ? mMainController.getPlayer() : null;
    }

    public Context getContext() {
        return mMainController != null ? mMainController.getActivity() : mContext;
    }

    public Activity getActivity() {
        return mMainController != null ? mMainController.getActivity() : null;
    }
    
    @Override
    public void onInit() {
        // NOP
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
    public void onScrollEnd(Video item) {
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
    public void onFinish() {
        // NOP
    }

    @Override
    public void onSourceChanged(Video item) {
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
    public void onEngineError(int type, int rendererIndex, Throwable error) {
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
    public void onSeekEnd() {
        // NOP
    }

    @Override
    public void onSpeedChanged(float speed) {
        // NOP
    }

    @Override
    public void onPlayEnd() {
        // NOP
    }

    @Override
    public void onBuffering() {
        // NOP
    }

    @Override
    public void onControlsShown(boolean shown) {
        // NOP
    }

    @Override
    public boolean onKeyDown(int keyCode) {
        // NOP
        return false;
    }

    @Override
    public void onHighQualityClicked() {
        // NOP
    }

    @Override
    public void onDislikeClicked(boolean dislike) {
        // NOP
    }

    @Override
    public void onLikeClicked(boolean like) {
        // NOP
    }

    @Override
    public void onChannelClicked() {
        // NOP
    }

    @Override
    public void onTrackSelected(FormatItem track) {
        // NOP
    }

    @Override
    public void onSubtitleClicked(boolean enabled) {
        // NOP
    }

    @Override
    public void onSubtitleLongClicked(boolean enabled) {
        // NOP
    }

    @Override
    public void onTrackChanged(FormatItem track) {
        // NOP
    }

    @Override
    public void onPlaylistAddClicked() {
        // NOP
    }

    @Override
    public void onDebugInfoClicked(boolean enabled) {
        // NOP
    }

    @Override
    public void onSpeedClicked(boolean enabled) {
        // NOP
    }

    @Override
    public void onSpeedLongClicked(boolean enabled) {
        // NOP
    }

    @Override
    public void onSeekIntervalClicked() {
        // NOP
    }

    @Override
    public void onChatClicked(boolean enabled) {
        // NOP
    }

    @Override
    public void onChatLongClicked(boolean enabled) {
        // NOP
    }

    @Override
    public void onVideoInfoClicked() {
        // NOP
    }

    @Override
    public void onShareLinkClicked() {
        // NOP
    }

    @Override
    public void onSearchClicked() {
        // NOP
    }

    @Override
    public void onVideoZoomClicked() {
        // NOP
    }

    @Override
    public void onPipClicked() {
        // NOP
    }

    @Override
    public void onPlaybackQueueClicked() {
        // NOP
    }

    @Override
    public void onButtonClicked(int buttonId, int buttonState) {
        // NOP
    }

    @Override
    public void onButtonLongClicked(int buttonId, int buttonState) {
        // NOP
    }

    @Override
    public void onTickle() {
        // NOP
    }
}
