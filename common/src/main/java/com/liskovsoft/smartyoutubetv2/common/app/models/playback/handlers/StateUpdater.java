package com.liskovsoft.smartyoutubetv2.common.app.models.playback.handlers;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.OptionItem;

import java.util.HashMap;
import java.util.Map;

public class StateUpdater extends PlayerEventListenerHelper {
    private final Map<Long, State> mPositionMap = new HashMap<>();
    private boolean mIsPlaying;
    private int mRepeatMode = 0;
    private OptionItem mVideoFormat;

    private static class State {
        final long positionMs;

        public State(long positionMs) {
            this.positionMs = positionMs;
        }
    }

    @Override
    public void openVideo(Video item) {
        mIsPlaying = true; // video just added
    }

    @Override
    public boolean onPreviousClicked() {
        boolean isShortVideo = mController.getPositionMs() > 10_000 && mController.getLengthMs() < 5*60*1000;

        if (isShortVideo) {
            mController.setPositionMs(0);
            return true;
        }

        saveState();
        return false;
    }

    @Override
    public boolean onNextClicked() {
        saveState();
        return false;
    }

    @Override
    public void onSuggestionItemClicked(Video item) {
        saveState();
    }

    @Override
    public void onEngineInitialized() {
        mController.setRepeatMode(mRepeatMode);
    }

    @Override
    public void onEngineReleased() {
        saveState();
    }

    @Override
    public void onVideoLoaded(Video item) {
        restoreState(item);
    }

    @Override
    public void onPlay() {
        mIsPlaying = true;
        Helpers.disableScreensaver(mActivity);
    }

    @Override
    public void onPause() {
        mIsPlaying = false;
        Helpers.enableScreensaver(mActivity);
    }

    @Override
    public void onRepeatModeChange(int modeIndex) {
        mRepeatMode = modeIndex;
    }

    private void saveState() {
        Video video = mController.getVideo();
        mPositionMap.put(video.id, new State(mController.getPositionMs()));
        mVideoFormat = mController.getVideoFormat();
    }

    private void restoreState(Video item) {
        State state = null;

        if (mVideoFormat != null) {
            mController.selectFormat(mVideoFormat);
        }

        if (mPositionMap.containsKey(item.id)) {
            state = mPositionMap.get(item.id);
        } else if (item.percentWatched > 0 && item.percentWatched < 100) {
            state = new State(getNewPosition(item.percentWatched));
        }

        boolean nearEnd = Math.abs(mController.getLengthMs() - mController.getPositionMs()) < 10_000;

        if (state != null && !nearEnd) {
            mController.setPositionMs(state.positionMs);
            mController.setPlay(mIsPlaying);
        } else {
            mController.setPlay(true); // start play immediately when state not found
        }
    }

    private long getNewPosition(int percentWatched) {
        long newPositionMs = mController.getLengthMs() / 100 * percentWatched;

        boolean samePositions = Math.abs(newPositionMs - mController.getPositionMs()) < 10_000;

        if (samePositions) {
            newPositionMs = -1;
        }

        return newPositionMs;
    }
}
