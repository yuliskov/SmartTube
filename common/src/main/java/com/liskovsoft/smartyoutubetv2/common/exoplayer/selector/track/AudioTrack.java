package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track;

import com.liskovsoft.sharedutils.helpers.Helpers;

public class AudioTrack extends MediaTrack {
    public AudioTrack(int rendererIndex) {
        super(rendererIndex);
    }

    @Override
    public int inBounds(MediaTrack track2) {
        int result = compare(track2);

        // Select at least something.
        if (result == -1 && track2 != null && track2.format != null) {
            result = 1;
        }

        return result;
    }

    @Override
    public int compare(MediaTrack track2) {
        if (format == null) {
            return -1;
        }

        if (track2 == null || track2.format == null) {
            return 1;
        }

        int result = -1;

        if (Helpers.equals(format.id, track2.format.id) && Helpers.equals(format.language, track2.format.language)) {
            result = 0;
        } else if (codecEquals(this, track2) && format.bitrate >= track2.format.bitrate) {
            result = 1;
        }

        return result;
    }
}
