package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller;

import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;

import java.io.InputStream;
import java.util.List;

public interface PlaybackEngineController {
    void openDash(InputStream dashManifest);
    void openHls(String hlsPlaylistUrl);
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
    void blockEngine();
    /**
     * Unblock engine from destroying
     */
    void unblockEngine();
}
