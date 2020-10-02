package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorUtil;

public class AudioTrack extends MediaTrack {
    public AudioTrack(int rendererIndex) {
        super(rendererIndex);
    }

    private static boolean codecEquals(String codecs1, String codecs2) {
        if (codecs1 == null || codecs2 == null) {
            return false;
        }

        return Helpers.equals(TrackSelectorUtil.codecNameShort(codecs1), TrackSelectorUtil.codecNameShort(codecs2));
    }

    @Override
    public int compare(MediaTrack track2) {
        if (track2.format == null) {
            return 1;
        }

        int result = 1;

        if (Helpers.equals(format.id, track2.format.id)) {
            result = 0;
        } else if (codecEquals(format.codecs, track2.format.codecs)) {
            return 0;
        }

        return result;
    }
}
