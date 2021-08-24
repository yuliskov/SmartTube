package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorUtil;

public class VideoTrack extends MediaTrack {
    private static final int SIZE_EQUITY_THRESHOLD_PERCENT = 5; // was 15 before
    private static final int COMPARE_TYPE_IN_BOUNDS = 0;
    private static final int COMPARE_TYPE_IN_BOUNDS_PROFILE = 1;
    private static final int COMPARE_TYPE_NORMAL = 2;

    public VideoTrack(int rendererIndex) {
        super(rendererIndex);
    }

    public static boolean sizeEquals(int size1, int size2) {
        return sizeEquals(size1, size2, SIZE_EQUITY_THRESHOLD_PERCENT);
    }

    public static boolean sizeEquals(int size1, int size2, int diffPercents) {
        if (size1 == -1 || size2 == -1) {
            return false;
        }

        int threshold = size1 / 100 * diffPercents;

        return Math.abs(size1 - size2) < threshold;
    }

    private static boolean sizeLessOrEquals(int size1, int size2) {
        if (size1 == -1 || size2 == -1) {
            return false;
        }

        return size1 <= size2 || sizeEquals(size1, size2);
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
    public int inBounds(MediaTrack track2) {
        if (format == null) {
            return -1;
        }

        // Detect profile based on format id presence
        return format.id == null ? compare(track2, COMPARE_TYPE_IN_BOUNDS_PROFILE) : compare(track2, COMPARE_TYPE_IN_BOUNDS);
    }

    @Override
    public int compare(MediaTrack track2) {
        return compare(track2, COMPARE_TYPE_NORMAL);
    }

    private int compare(MediaTrack track2, int type) {
        if (format == null) {
            return -1;
        }

        if (track2 == null || track2.format == null) {
            return 1;
        }

        int size1;
        int size2;

        if (format.width > format.height && track2.format.width > track2.format.height) {
            size1 = format.width;
            size2 = track2.format.width;
        } else {
            size1 = format.height;
            size2 = track2.format.height;
        }

        String id1 = format.id;
        String id2 = track2.format.id;
        // Low fps (e.g. 8fps) on original track could break whole comparison
        float frameRate1 = format.frameRate < 10 ? 30 : format.frameRate;
        float frameRate2 = track2.format.frameRate;
        String codecs1 = format.codecs;
        String codecs2 = track2.format.codecs;

        int result;

        if (type == COMPARE_TYPE_IN_BOUNDS) {
            result = inBounds(id1, id2, size1, size2, frameRate1, frameRate2, codecs1, codecs2);
        } else if (type == COMPARE_TYPE_IN_BOUNDS_PROFILE) {
            result = inBoundsProfile(id1, id2, size1, size2, frameRate1, frameRate2, codecs1, codecs2);
        } else {
            result = compare(id1, id2, size1, size2, frameRate1, frameRate2, codecs1, codecs2);
        }

        return result;
    }

    private int inBounds(String id1, String id2, int size1, int size2, float frameRate1, float frameRate2, String codecs1, String codecs2) {
        int result = -1;

        if (Helpers.equals(id1, id2)) {
            result = 0;
        } else if (sizeLessOrEquals(size2, size1) && fpsLessOrEquals(frameRate2, frameRate1)) {
            if (TrackSelectorUtil.isHdrCodec(codecs1) == TrackSelectorUtil.isHdrCodec(codecs2)) {
                result = 1;
            } else if (TrackSelectorUtil.isHdrCodec(codecs1)) {
                result = 1;
            }
        }

        return result;
    }

    private int inBoundsProfile(String id1, String id2, int size1, int size2, float frameRate1, float frameRate2, String codecs1, String codecs2) {
        int result = -1;

        if (Helpers.equals(id1, id2)) {
            result = 0;
        } else if (sizeEquals(size1, size2)) {
            if (fpsEquals(frameRate2, frameRate1)) {
                if (TrackSelectorUtil.isHdrCodec(codecs1) == TrackSelectorUtil.isHdrCodec(codecs2)) {
                    result = 0;
                } else if (TrackSelectorUtil.isHdrCodec(codecs1)) {
                    result = 1;
                }
            } else if (fpsLessOrEquals(frameRate2, frameRate1)) {
                if (TrackSelectorUtil.isHdrCodec(codecs1) == TrackSelectorUtil.isHdrCodec(codecs2)) {
                    result = 1;
                } else if (TrackSelectorUtil.isHdrCodec(codecs1)) {
                    result = 1;
                }
            }
        } else if (sizeLessOrEquals(size2, size1) && fpsLessOrEquals(frameRate2, frameRate1)) {
            if (TrackSelectorUtil.isHdrCodec(codecs1) == TrackSelectorUtil.isHdrCodec(codecs2)) {
                result = 1;
            } else if (TrackSelectorUtil.isHdrCodec(codecs1)) {
                result = 1;
            }
        }

        return result;
    }

    private int compare(String id1, String id2, int size1, int size2, float frameRate1, float frameRate2, String codecs1, String codecs2) {
        int result = -1;

        if (Helpers.equals(id1, id2)) {
            result = 0;
        } else if (sizeLessOrEquals(size2, size1)) {
            if (fpsLessOrEquals(frameRate2, frameRate1)) {
                if (TrackSelectorUtil.isHdrCodec(codecs1) == TrackSelectorUtil.isHdrCodec(codecs2)) {
                    result = 0;
                } else if (TrackSelectorUtil.isHdrCodec(codecs2)) {
                    result = -1;
                } else {
                    result = 1;
                }
            }
        }

        return result;
    }
}
