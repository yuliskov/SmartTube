package com.liskovsoft.smartyoutubetv2.common.mvp.views;

import com.liskovsoft.smartyoutubetv2.common.mvp.models.Video;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.playback.player.PlaybackState;

import java.io.InputStream;

public interface PlaybackView {
    void updateRelated(VideoGroup group);
    void clearRelated();
    void initTitle(Video video);
    void openDash(InputStream dashManifest);
    void openHls(String hlsPlaylistUrl);
    void syncState(PlaybackState state);
}
