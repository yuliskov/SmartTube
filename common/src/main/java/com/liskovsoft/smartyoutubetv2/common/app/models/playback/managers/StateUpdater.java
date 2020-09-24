package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;

import java.util.HashMap;
import java.util.Map;

public class StateUpdater extends PlayerEventListenerHelper {
    private boolean mIsPlaying;
    private int mRepeatMode = 0;
    private FormatItem mVideoFormat = FormatItem.HD_AVC;
    private static final long MUSIC_VIDEO_LENGTH_MS = 6 * 60 * 1000;
    // Don't store state inside Video object.
    // As one video might correspond to multiple Video objects.
    private final Map<String, State> mStates = new HashMap<>();

    private static class State {
        public final long positionMs;

        public State(long positionMs) {
            this.positionMs = positionMs;
        }
    }

    @Override
    public void openVideo(Video item) {
        mIsPlaying = true; // video just added

        // Ensure that we aren't running on presenter init stage
        if (mController != null) {
            saveState();
        }
    }

    @Override
    public boolean onPreviousClicked() {
        boolean isShortVideo = mController.getPositionMs() > 10_000 && mController.getLengthMs() < MUSIC_VIDEO_LENGTH_MS;

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

    @Override
    public void onTrackClicked(FormatItem track) {
        if (track.getType() == FormatItem.TYPE_VIDEO) {
            mVideoFormat = track;
        }
    }

    private void saveState() {
        trimStorage();

        Video video = mController.getVideo();

        if (video != null) {
            mStates.put(video.videoId, new State(mController.getPositionMs()));
        }
    }

    private void trimStorage() {
        // NOP
    }

    private void restoreState(Video item) {
        if (mVideoFormat != null) {
            mController.selectFormat(mVideoFormat);
        }

        State state = mStates.get(item.videoId);

        // internal storage has priority over item data loaded from network
        if (state == null && item.percentWatched > 0 && item.percentWatched < 100) {
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
