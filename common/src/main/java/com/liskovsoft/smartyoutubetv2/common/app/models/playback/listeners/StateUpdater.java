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
        State state = mPositionMap.get(item.id);

        boolean nearEnd = Math.abs(mController.getLengthMs() - mController.getPositionMs()) < 10_000;

        if (state != null && !nearEnd) {
            mController.setPositionMs(state.positionMs);
            mController.setPlay(state.isPlaying);
        } else {
            mController.setPlay(true); // start play immediately by default
        }
    }

    public void onMetadataLoaded(MediaItemMetadata mediaItemMetadata) {
        if (mPositionMap.get(mController.getVideo().id) == null) {
            boolean isUserSeeking = mController.getPositionMs() > 10_000;
            if (!isUserSeeking) {
                long newPositionMs = mController.getLengthMs() / 100 * mediaItemMetadata.getPercentWatched();

                boolean positionsMismatch = Math.abs(newPositionMs - mController.getPositionMs()) > 10_000;

                if (positionsMismatch) {
                    mController.setPositionMs(newPositionMs);
                }
            }
        }
    }
}
