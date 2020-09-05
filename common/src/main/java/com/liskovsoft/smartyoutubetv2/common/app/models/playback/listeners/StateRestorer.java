package com.liskovsoft.smartyoutubetv2.common.app.models.playback.listeners;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;

import java.util.HashMap;
import java.util.Map;

public class StateRestorer extends PlayerEventListenerHelper {
    private Map<String, State> mPositionMap = new HashMap<>();

    private static class State {
        long positionMs;
        boolean isPlaying;

        public State(long positionMs, boolean isPlaying) {
            this.positionMs = positionMs;
            this.isPlaying = isPlaying;
        }
    }

    @Override
    public void onPrevious() {
        saveState();
    }

    @Override
    public void onNext() {
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
