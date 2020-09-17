package com.liskovsoft.smartyoutubetv2.common.exoplayer.managers;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;

import java.io.InputStream;
import java.util.List;

public interface PlayerController {
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
    List<OptionItem> getVideoFormats();
    List<OptionItem> getAudioFormats();
    List<OptionItem> getSubtitleFormats();
    void selectFormat(OptionItem option);
    OptionItem getCurrentFormat();
}
