package com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager;

import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;

import java.io.InputStream;
import java.util.List;

public interface PlayerEngine extends PlayerEngineConstants {
    void openDash(InputStream dashManifest);
    void openDashUrl(String dashManifestUrl);
    void openHlsUrl(String hlsPlaylistUrl);
    void openUrlList(List<String> urlList);
    void openMerged(InputStream dashManifest, String hlsPlaylistUrl);
    long getPositionMs();
    void setPositionMs(long positionMs);
    long getDurationMs();
    void setPlayWhenReady(boolean play);
    boolean getPlayWhenReady();
    boolean isPlaying();
    boolean isLoading();
    List<FormatItem> getVideoFormats();
    List<FormatItem> getAudioFormats();
    List<FormatItem> getSubtitleFormats();
    void setFormat(FormatItem option);
    FormatItem getVideoFormat();
    FormatItem getAudioFormat();
    boolean isEngineInitialized();
    void restartEngine();
    void reloadPlayback();
    void blockEngine(boolean block);
    boolean isEngineBlocked();
    //void setBackgroundMode(int type);
    //int getBackgroundMode();
    boolean isInPIPMode();
    boolean containsMedia();
    void setSpeed(float speed);
    float getSpeed();
    void setPitch(float pitch);
    float getPitch();
    void setVolume(float volume);
    float getVolume();
    void setVideoZoomMode(int mode);
    void setVideoZoom(int percents);
    int getVideoZoomMode();
    void setVideoAspectRatio(float mode);
    void setVideoRotation(int angle);
    void setVideoFlipEnabled(boolean enabled);
}
