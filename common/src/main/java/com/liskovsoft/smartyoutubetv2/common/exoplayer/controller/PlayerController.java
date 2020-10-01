package com.liskovsoft.smartyoutubetv2.common.exoplayer.controller;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;

import java.io.InputStream;
import java.util.List;

public interface PlayerController {
    int BUFFER_LOW = 0;
    int BUFFER_MED = 1;
    int BUFFER_HIGH = 2;
    int BUFFER_MAX = 3;
    void openDash(InputStream dashManifest);
    void openHls(String hlsPlaylistUrl);
    void openUrlList(List<String> urlList);
    long getPositionMs();
    void setPositionMs(long positionMs);
    long getLengthMs();
    void setPlay(boolean isPlaying);
    boolean isPlaying();
    void setEventListener(PlayerEventListener eventListener);
    void setVideo(Video video);
    Video getVideo();
    void setRepeatMode(int modeIndex);
    List<FormatItem> getVideoFormats();
    List<FormatItem> getAudioFormats();
    List<FormatItem> getSubtitleFormats();
    void selectFormat(FormatItem option);
    FormatItem getVideoFormat();
    boolean hasNoMedia();
    void setSpeed(float speed);
    float getSpeed();
}
