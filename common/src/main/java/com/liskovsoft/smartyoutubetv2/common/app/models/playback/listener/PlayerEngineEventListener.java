package com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;

public interface PlayerEngineEventListener {
    void onPlay();
    void onPause();
    void onSeek();
    void onVideoLoaded(Video item);
    void onEngineInitialized();
    void onEngineReleased();
    void onPlayEnd();
    void onRepeatModeChange(int modeIndex);
    void onTrackChanged(FormatItem track);
}
