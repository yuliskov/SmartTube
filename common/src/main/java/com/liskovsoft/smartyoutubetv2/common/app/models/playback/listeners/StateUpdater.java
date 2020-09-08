package com.liskovsoft.smartyoutubetv2.common.app.models.playback.listeners;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;

import java.util.HashMap;
import java.util.Map;

public class StateUpdater extends PlayerEventListenerHelper {
    private final Map<Long, State> mPositionMap = new HashMap<>();

    private static class State {
        final long positionMs;
        final boolean isPlaying;

        public State(long positionMs, boolean isPlaying) {
            this.positionMs = positionMs;
            this.isPlaying = isPlaying;
        }
    }

    @Override
    public void onPreviousClicked() {
        saveState();
    }

    @Override
    public void onNextClicked() {
        saveState();
    }

    @Override
    public void onSuggestionItemClicked(Video item) {
        saveState();
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
        Helpers.disableScreensaver(mActivity);
    }

    @Override
    public void onPause() {
        Helpers.enableScreensaver(mActivity);
    }

    private void saveState() {
        Video video = mController.getVideo();
        mPositionMap.put(video.id, new State(mController.getPositionMs(), mController.isPlaying()));
    }

    private void restoreState(Video item) {
        State state = null;

        if (item.percentWatched > 0 && item.percentWatched < 100) {
            state = new State(getNewPosition(item.percentWatched), true);
        } else {
            state = mPositionMap.get(item.id);
        }

        boolean nearEnd = Math.abs(mController.getLengthMs() - mController.getPositionMs()) < 10_000;

        if (state != null && !nearEnd) {
            mController.setPositionMs(state.positionMs);
            mController.setPlay(state.isPlaying);
        } else {
            mController.setPlay(true); // start play immediately by default
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
