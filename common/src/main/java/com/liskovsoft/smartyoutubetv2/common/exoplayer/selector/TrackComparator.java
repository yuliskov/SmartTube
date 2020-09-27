package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager.MediaTrack;

class TrackComparator {
    private static final int HEIGHT_EQUITY_THRESHOLD_PX = 80;
    private int mRendererIndex;

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
        if (mRendererIndex == TrackSelectorManager.RENDERER_INDEX_VIDEO) {
            return compareVideo(track1, track2);
        } else if (mRendererIndex == TrackSelectorManager.RENDERER_INDEX_AUDIO) {
            return compareAudio(track1, track2);
        }

        return 0;
    }

    private int compareAudio(MediaTrack track1, MediaTrack track2) {
        if (track1 == null || track1.format == null) {
            return -1;
        }

        int result = 1;

        if (Helpers.equals(track1.format.id, track2.format.id)) {
            result = 0;
        } else if (TrackComparator.codecEquals(track1.format.codecs, track2.format.codecs)) {
            return 0;
        }

        return result;
    }

    private static int compareVideo(MediaTrack track1, MediaTrack track2) {
        if (track1 == null || track1.format == null) {
            return -1;
        }

        int result = 1;

        if (Helpers.equals(track1.format.id, track2.format.id)) {
            result = 0;
        } else if (TrackComparator.codecEquals(track1.format.codecs, track2.format.codecs)) {
            if (TrackComparator.fpsLessOrEquals(track1.format.frameRate, track2.format.frameRate)) {
                if (TrackComparator.heightEquals(track1.format.height, track2.format.height)) {
                    result = 0;
                } else if (TrackComparator.heightLessOrEquals(track1.format.height, track2.format.height)) {
                    result = -1;
                }
            }
        }

        return result;
    }

    public void setRendererIndex(int rendererIndex) {
        mRendererIndex = rendererIndex;
    }
}
