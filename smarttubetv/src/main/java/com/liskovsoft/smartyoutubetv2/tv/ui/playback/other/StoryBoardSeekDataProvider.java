package com.liskovsoft.smartyoutubetv2.tv.ui.playback.other;

import androidx.leanback.media.PlaybackGlue;
import androidx.leanback.media.PlaybackTransportControlGlue;
import androidx.leanback.widget.PlaybackSeekDataProvider;

public class StoryBoardSeekDataProvider extends PlaybackSeekDataProvider {
    public StoryBoardSeekDataProvider(long duration, long interval) {
        
    }

    public static void setSeekProvider(PlaybackTransportControlGlue<?> glue) {
        if (glue.isPrepared()) {
            glue.setSeekProvider(new StoryBoardSeekDataProvider(
                    glue.getDuration(),
                    glue.getDuration() / 100));
        } else {
            glue.addPlayerCallback(new PlaybackGlue.PlayerCallback() {
                @Override
                public void onPreparedStateChanged(PlaybackGlue glue) {
                    if (glue.isPrepared()) {
                        glue.removePlayerCallback(this);
                        PlaybackTransportControlGlue<?> transportControlGlue =
                                (PlaybackTransportControlGlue<?>) glue;
                        transportControlGlue.setSeekProvider(new StoryBoardSeekDataProvider(
                                transportControlGlue.getDuration(),
                                transportControlGlue.getDuration() / 100));
                    }
                }
            });
        }
    }
}
