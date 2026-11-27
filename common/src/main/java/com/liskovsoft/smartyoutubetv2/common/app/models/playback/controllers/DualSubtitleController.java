package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;

public class DualSubtitleController extends BasePlayerController {
    @Override
    public void onVideoLoaded(Video item) {
        if (getPlayer() != null) {
            getPlayer().refreshDualSubtitles();
        }
    }

    @Override
    public void onTrackChanged(FormatItem track) {
        if (track != null && track.getType() == FormatItem.TYPE_SUBTITLE && getPlayer() != null) {
            getPlayer().refreshDualSubtitles();
        }
    }

    @Override
    public void onTrackSelected(FormatItem track) {
        if (track != null && track.getType() == FormatItem.TYPE_SUBTITLE && getPlayer() != null) {
            getPlayer().refreshDualSubtitles();
        }
    }
}
