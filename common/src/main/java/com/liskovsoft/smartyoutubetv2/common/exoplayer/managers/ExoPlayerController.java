package com.liskovsoft.smartyoutubetv2.common.exoplayer.managers;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;

import java.io.InputStream;

public interface ExoPlayerController {
    void openDash(InputStream dashManifest);
    void openHls(String hlsPlaylistUrl);
    long getPosition();
    void setPosition(long positionMs);
    long getLengthMs();
    void setPlay(boolean isPlaying);
    boolean isPlaying();
    void setEventListener(PlayerEventListener eventListener);
    void setVideo(Video video);
    Video getVideo();
    void setRepeatMode(int modeIndex);
}
