package com.liskovsoft.smartyoutubetv2.common.exoplayer.comparator;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager.MediaTrack;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorUtil;

public class AudioTrackComparator extends TrackComparator {
    private static boolean codecEquals(String codecs1, String codecs2) {
        if (codecs1 == null || codecs2 == null) {
            return false;
        }

        return Helpers.equals(TrackSelectorUtil.codecNameShort(codecs1), TrackSelectorUtil.codecNameShort(codecs2));
    }

    public int compare(MediaTrack track1, MediaTrack track2) {
        if (track1 == null || track1.format == null) {
            return -1;
        }

        int result = 1;

        if (Helpers.equals(track1.format.id, track2.format.id)) {
            result = 0;
        } else if (codecEquals(track1.format.codecs, track2.format.codecs)) {
            return 0;
        }

        return result;
    }
}
