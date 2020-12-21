package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track;

import com.liskovsoft.sharedutils.helpers.Helpers;

public class AudioTrack extends MediaTrack {
    public AudioTrack(int rendererIndex) {
        super(rendererIndex);
    }

    @Override
    public int inBounds(MediaTrack track2) {
        return compare(track2);
    }

    @Override
    public int compare(MediaTrack track2) {
        if (track2.format == null) {
            return 1;
        }

        int result = -1;

        if (Helpers.equals(format.id, track2.format.id)) {
            result = 0;
        } else if (codecEquals(format.codecs, track2.format.codecs)) {
            result = 1;
        }

        return result;
    }
}
