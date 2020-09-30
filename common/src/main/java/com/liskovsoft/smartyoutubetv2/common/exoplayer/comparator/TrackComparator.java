package com.liskovsoft.smartyoutubetv2.common.exoplayer.comparator;

import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager.MediaTrack;

public abstract class TrackComparator {
    public static TrackComparator forRenderer(int rendererIndex) {
        if (rendererIndex == TrackSelectorManager.RENDERER_INDEX_VIDEO) {
            return new VideoTrackComparator();
        } else if (rendererIndex == TrackSelectorManager.RENDERER_INDEX_AUDIO) {
            return new AudioTrackComparator();
        } else if (rendererIndex == TrackSelectorManager.RENDERER_INDEX_SUBTITLE) {
            return new SubtitleTrackComparator();
        }

        return null;
    }
    public abstract int compare(MediaTrack track1, MediaTrack track2);
}
