package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorUtil;

public class VideoTrack extends MediaTrack {
    private static final int HEIGHT_EQUITY_THRESHOLD_PX = 80;

    public VideoTrack(int rendererIndex) {
        super(rendererIndex);
    }

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
            return true; // probably LIVE translation
        }

        return fps1 <= fps2 || fpsEquals(fps1, fps2);
    }

    private boolean isLive(MediaTrack track) {
        return track.format.frameRate == -1;
    }

    @Override
    public int compare(MediaTrack track2) {
        if (track2.format == null) {
            return 1;
        }

        int result = -1;

        if (Helpers.equals(format.id, track2.format.id)) {
            result = 0;
        } else if (codecEquals(format.codecs, track2.format.codecs) || isLive(track2)) {
            if (fpsLessOrEquals(track2.format.frameRate, format.frameRate)) {
                if (heightEquals(format.height, track2.format.height)) {
                    if (TrackSelectorUtil.isHdrCodec(format.codecs) == TrackSelectorUtil.isHdrCodec(track2.format.codecs)) {
                        result = 0;
                    } else {
                        result = 1;
                    }
                } else if (heightLessOrEquals(track2.format.height, format.height)) {
                    result = 1;
                }
            }
        }

        return result;
    }
}
