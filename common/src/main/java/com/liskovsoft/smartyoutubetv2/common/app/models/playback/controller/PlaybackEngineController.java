package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller;

import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;

import java.io.InputStream;
import java.util.List;

public interface PlaybackEngineController {
    void openDash(InputStream dashManifest);
    void openHls(String hlsPlaylistUrl);
    void openUrlList(List<String> urlList);
    long getPositionMs();
    void setPositionMs(long positionMs);
    long getLengthMs();
    void setPlay(boolean play);
    boolean isPlaying();
    void setRepeatMode(int modeIndex);
    List<FormatItem> getVideoFormats();
    List<FormatItem> getAudioFormats();
    void selectFormat(FormatItem option);
    FormatItem getVideoFormat();
    /**
     * Block engine from destroying
     */
    void blockEngine(boolean block);
    boolean isEngineBlocked();
    void restartEngine();
    void enablePIP(boolean enable);
    boolean isPIPEnabled();
    boolean isInPIPMode();
}
