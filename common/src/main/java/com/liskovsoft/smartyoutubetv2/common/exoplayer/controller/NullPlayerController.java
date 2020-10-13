package com.liskovsoft.smartyoutubetv2.common.exoplayer.controller;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;

import java.io.InputStream;
import java.util.List;

public class NullPlayerController implements PlayerController {
    @Override
    public void openDash(InputStream dashManifest) {
        
    }

    @Override
    public void openHls(String hlsPlaylistUrl) {

    }

    @Override
    public void openUrlList(List<String> urlList) {

    }

    @Override
    public long getPositionMs() {
        return 0;
    }

    @Override
    public void setPositionMs(long positionMs) {

    }

    @Override
    public long getLengthMs() {
        return 0;
    }

    @Override
    public void setPlay(boolean isPlaying) {

    }

    @Override
    public boolean isPlaying() {
        return false;
    }

    @Override
    public void setEventListener(PlayerEventListener eventListener) {

    }

    @Override
    public void setVideo(Video video) {

    }

    @Override
    public Video getVideo() {
        return null;
    }

    @Override
    public List<FormatItem> getVideoFormats() {
        return null;
    }

    @Override
    public List<FormatItem> getAudioFormats() {
        return null;
    }

    @Override
    public List<FormatItem> getSubtitleFormats() {
        return null;
    }

    @Override
    public void selectFormat(FormatItem option) {

    }

    @Override
    public FormatItem getVideoFormat() {
        return null;
    }

    @Override
    public boolean hasNoMedia() {
        return false;
    }

    @Override
    public void setSpeed(float speed) {

    }

    @Override
    public float getSpeed() {
        return 0;
    }
}
