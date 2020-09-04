package com.liskovsoft.smartyoutubetv2.common.exoplayer.managers;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.trackselection.TrackSelector;

public class ExoPlayerController {
    private final ExoPlayer mPlayer;
    private final TrackSelector mTrackSelector;

    public ExoPlayerController(ExoPlayer player, TrackSelector trackSelector) {
        mPlayer = player;
        mTrackSelector = trackSelector;
    }

    public long getPosition() {
        return mPlayer.getCurrentPosition();
    }

    public void setPosition(long positionMs) {
        mPlayer.seekTo(positionMs);
    }
}
