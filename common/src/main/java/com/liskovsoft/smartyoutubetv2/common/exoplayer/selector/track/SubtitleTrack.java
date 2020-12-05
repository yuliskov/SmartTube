package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track;

import com.liskovsoft.sharedutils.helpers.Helpers;

public class SubtitleTrack extends MediaTrack {
    public SubtitleTrack(int rendererIndex) {
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
        } else if (Helpers.equals(format.language, track2.format.language)) {
            return 0;
        }

        return result;
    }
}
