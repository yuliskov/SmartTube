package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller;

import java.io.InputStream;

public interface PlayerEngineController {
    void openDash(InputStream dashManifest);
    void openHls(String hlsPlaylistUrl);
    long getPositionMs();
    void setPositionMs(long positionMs);
    long getLengthMs();
    void setPlay(boolean play);
    boolean isPlaying();
    void setRepeatMode(int modeIndex);
}
