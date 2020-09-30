package com.liskovsoft.smartyoutubetv2.common.exoplayer.comparator;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager.MediaTrack;

public class SubtitleTrackComparator extends TrackComparator {
    @Override
    public int compare(MediaTrack track1, MediaTrack track2) {
        if (track1 == null || track1.format == null) {
            return -1;
        }

        int result = 1;

        if (Helpers.equals(track1.format.id, track2.format.id)) {
            result = 0;
        } else if (Helpers.equals(track1.format.language, track2.format.language)) {
            return 0;
        }

        return result;
    }
}
