package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller;

import java.io.InputStream;
import java.util.List;

public interface PlayerEngineController {
    void openDash(InputStream dashManifest);
    void openHls(String hlsPlaylistUrl);
    long getPositionMs();
    void setPositionMs(long positionMs);
    long getLengthMs();
    void setPlay(boolean play);
    boolean isPlaying();
    void setRepeatMode(int modeIndex);
    List<OptionItem> getVideoFormats();
    List<OptionItem> getAudioFormats();
    void selectFormat(OptionItem option);
    OptionItem getVideoFormat();
    /**
     * Block engine from destroying
     */
    void blockEngine();
    /**
     * Unblock engine from destroying
     */
    void unblockEngine();
}
