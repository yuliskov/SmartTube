package com.liskovsoft.smartyoutubetv2.common.app.models.playback.listeners;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;

import java.util.HashMap;
import java.util.Map;

public class StateRestorer extends PlayerEventListenerHelper {
    private final Map<String, State> mPositionMap = new HashMap<>();

    private static class State {
        long positionMs;
        boolean isPlaying;

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
    public void onVideoLoaded(Video item) {
        restoreState(item);
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
    public void onPlay() {
        Helpers.disableScreensaver(mActivity);
    }

    @Override
    public void onPause() {
        Helpers.enableScreensaver(mActivity);
    }

    private void saveState() {
        Video video = mController.getVideo();
        mPositionMap.put(video.title + video.description, new State(mController.getPositionMs(), mController.isPlaying()));
    }

    private void restoreState(Video item) {
        State state = mPositionMap.get(item.title + item.description);

        if (state != null) {
            mController.setPositionMs(state.positionMs);
            mController.setPlay(state.isPlaying);
        } else {
            mController.setPlay(true); // start play immediately by default
        }
    }
}
