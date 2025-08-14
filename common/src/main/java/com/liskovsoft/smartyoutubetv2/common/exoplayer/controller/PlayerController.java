package com.liskovsoft.smartyoutubetv2.common.exoplayer.controller;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;

import java.io.InputStream;
import java.util.List;

public interface PlayerController {
    void openSabr(MediaItemFormatInfo formatInfo);
    void openDash(InputStream dashManifest);
    void openHlsUrl(String hlsPlaylistUrl);
    void openDashUrl(String dashManifestUrl);
    void openUrlList(List<String> urlList);
    void openMerged(InputStream dashManifest, String hlsPlaylistUrl);
    long getPositionMs();
    void setPositionMs(long positionMs);
    long getDurationMs();
    void setPlayWhenReady(boolean play);
    boolean getPlayWhenReady();
    boolean isPlaying();
    boolean isLoading();
    void release();
    void setPlayer(SimpleExoPlayer player);
    //void setEventListener(PlayerEventListener eventListener);
    void setPlayerView(PlayerView playerView);
    void setTrackSelector(DefaultTrackSelector trackSelector);
    void setVideo(Video video);
    Video getVideo();
    List<FormatItem> getVideoFormats();
    List<FormatItem> getAudioFormats();
    List<FormatItem> getSubtitleFormats();
    void selectFormat(FormatItem option);
    FormatItem getVideoFormat();
    FormatItem getAudioFormat();
    FormatItem getSubtitleFormat();
    boolean containsMedia();
    void setSpeed(float speed);
    float getSpeed();
    void setPitch(float pitch);
    float getPitch();
    void setVolume(float volume);
    float getVolume();
    void resetPlayerState();
    void setOnVideoLoaded(Runnable onVideoLoaded);
}
