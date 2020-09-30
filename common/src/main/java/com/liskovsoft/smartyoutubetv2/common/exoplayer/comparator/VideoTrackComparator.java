package com.liskovsoft.smartyoutubetv2.common.exoplayer.comparator;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager.MediaTrack;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorUtil;

public class VideoTrackComparator extends TrackComparator {
    private static final int HEIGHT_EQUITY_THRESHOLD_PX = 80;

    private static boolean codecEquals(String codecs1, String codecs2) {
        if (codecs1 == null || codecs2 == null) {
            return false;
        }

        return Helpers.equals(TrackSelectorUtil.codecNameShort(codecs1), TrackSelectorUtil.codecNameShort(codecs2));
    }

    private static boolean heightEquals(int height1, int height2) {
        if (height1 == -1 || height2 == -1) {
            return false;
        }

        return Math.abs(height1 - height2) < HEIGHT_EQUITY_THRESHOLD_PX;
    }

    private static boolean heightLessOrEquals(int height1, int height2) {
        if (height1 == -1 || height2 == -1) {
            return false;
        }

        return height1 <= height2 || heightEquals(height1, height2);
    }

    private static boolean fpsEquals(float fps1, float fps2) {
        if (fps1 == -1 || fps2 == -1) {
            return true;
        }

        return Math.abs(fps1 - fps2) < 10;
    }

    private static boolean fpsLessOrEquals(float fps1, float fps2) {
        if (fps1 == -1 || fps2 == -1) {
            return true; // probably live translation
        }

        return fps1 <= fps2 || fpsEquals(fps1, fps2);
    }

    public int compare(MediaTrack track1, MediaTrack track2) {
        if (track1 == null || track1.format == null) {
            return -1;
        }

        int result = 1;

        if (Helpers.equals(track1.format.id, track2.format.id)) {
            result = 0;
        } else if (VideoTrackComparator.codecEquals(track1.format.codecs, track2.format.codecs)) {
            if (VideoTrackComparator.fpsLessOrEquals(track1.format.frameRate, track2.format.frameRate)) {
                if (VideoTrackComparator.heightEquals(track1.format.height, track2.format.height)) {
                    result = 0;
                } else if (VideoTrackComparator.heightLessOrEquals(track1.format.height, track2.format.height)) {
                    result = -1;
                }
            }
        }

        return result;
    }
}
