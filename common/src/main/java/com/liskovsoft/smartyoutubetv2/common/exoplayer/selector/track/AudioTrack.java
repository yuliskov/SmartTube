package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorUtil;

public class AudioTrack extends MediaTrack {
    private static final int BITRATE_DIFF_PERCENTS = 5;

    public AudioTrack(int rendererIndex) {
        super(rendererIndex);
    }

    //@Override
    //public int inBounds(MediaTrack track2) {
    //    int result = compare(track2);
    //
    //    // Select at least something.
    //    if (result == -1 && track2 != null && track2.format != null) {
    //        result = 1;
    //    }
    //
    //    return result;
    //}

    @Override
    public int inBounds(MediaTrack track2) {
        if (format == null) {
            return -1;
        }

        if (track2 == null || track2.format == null) {
            return 1;
        }

        int result = -1;

        String id1 = format.id;
        String id2 = track2.format.id;
        int bitrate1 = format.bitrate;
        int bitrate2 = track2.format.bitrate;

        // Compare by language isn't robust since language set may not contain target language
        String language1 = format.language;
        String language2 = track2.format.language;
        boolean sameLanguage = sameLanguage(language1, language2);

        if (Helpers.equals(id1, id2)) {
            result = 0;
        } else if (bitrate1 != -1 && bitrateLessOrEquals(bitrate2, bitrate1)) {
            result = 1;
        } else if (bitrate1 == -1 && (TrackSelectorUtil.is51Audio(format) || !TrackSelectorUtil.is51Audio(track2.format))) {
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

        if (format.id == null && format.language == null && format.bitrate == -1 && codecEquals(this, track2)) {
            result = 0;
        } else if (Helpers.equals(format.id, track2.format.id)) {
            result = 1;
        } else if (!codecEquals(this, track2) || format.bitrate >= track2.format.bitrate ||
                Math.abs(format.bitrate - track2.format.bitrate) < (format.bitrate / 100 * BITRATE_DIFF_PERCENTS)) {
            result = 0;
        }

        return result;
    }

    private boolean sameLanguage(String language1, String language2) {
        return Helpers.equals(language1, language2) || (language1 == null || language2 == null);
    }
}
