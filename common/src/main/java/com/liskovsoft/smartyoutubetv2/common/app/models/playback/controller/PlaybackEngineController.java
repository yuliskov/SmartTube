package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller;

import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;

import java.io.InputStream;
import java.util.List;

public interface PlaybackEngineController {
    int BUFFER_LOW = 0;
    int BUFFER_MED = 1;
    int BUFFER_HIGH = 2;
    void openDash(InputStream dashManifest);
    void openHls(String hlsPlaylistUrl);
    void openUrlList(List<String> urlList);
    long getPositionMs();
    void setPositionMs(long positionMs);
    long getLengthMs();
    void setPlay(boolean play);
    boolean isPlaying();
    List<FormatItem> getVideoFormats();
    List<FormatItem> getAudioFormats();
    List<FormatItem> getSubtitleFormats();
    void selectFormat(FormatItem option);
    FormatItem getVideoFormat();
    /**
     * Block engine from destroying
     */
    void blockEngine(boolean block);
    boolean isEngineBlocked();
    void restartEngine();
    void reloadPlayback();
    void enablePIP(boolean enable);
    boolean isPIPEnabled();
    boolean isInPIPMode();
    boolean hasNoMedia();
    void setSpeed(float speed);
    float getSpeed();
    void setBuffer(int bufferType);
    int getBuffer();
}
