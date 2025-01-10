package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track;

import com.google.android.exoplayer2.Format;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorUtil;

public class VideoTrack extends MediaTrack {
    private static final float LOW_FPS_THRESHOLD = 10;
    private static final int SIZE_EQUITY_THRESHOLD_PERCENT = 5; // was 15 before
    private static final int COMPARE_TYPE_IN_BOUNDS = 0;
    private static final int COMPARE_TYPE_IN_BOUNDS_NO_FPS = 4;
    private static final int COMPARE_TYPE_IN_BOUNDS_PRESET = 1;
    private static final int COMPARE_TYPE_IN_BOUNDS_PRESET_NO_FPS = 3;
    private static final int COMPARE_TYPE_NORMAL = 2;
    public static boolean sIsNoFpsPresetsEnabled;

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
        boolean diffWithinThreshold = Math.abs(size1 - size2) < threshold;

        return diffWithinThreshold;
    }

    private static boolean sizeLessOrEquals(int size1, int size2) {
        if (size1 == -1 || size2 == -1) {
            return false;
        }

        return size1 <= size2 || sizeEquals(size1, size2);
    }

    private static boolean sizeLess(int size1, int size2) {
        if (size1 == -1 || size2 == -1) {
            return false;
        }

        return !sizeEquals(size1, size2) && size1 < size2;
    }

    private static boolean fpsEquals(float fps1, float fps2) {
        if (fps1 == -1 || fps2 == -1) {
            return true; // probably LIVE translation
        }

        int threshold = 10;
        boolean diffWithinThreshold = Math.abs(fps1 - fps2) < threshold;

        return diffWithinThreshold;
    }

    private static boolean fpsLessOrEquals(float fps1, float fps2) {
        if (fps1 == -1 || fps2 == -1) {
            return true; // probably LIVE translation
        }

        return fps1 <= fps2 || fpsEquals(fps1, fps2);
    }

    private static boolean fpsLess(float fps1, float fps2) {
        // NOTE: commented out after no fps fix option
        //if (fps1 == -1 || fps2 == -1) {
        //    return true; // probably LIVE translation
        //}

        if (fps1 == -1 && fps2 == -1) {
            return false;
        }
        
        return !fpsEquals(fps1, fps2) && fps1 < fps2;
    }

    private boolean isLive(MediaTrack track) {
        return track.format.frameRate == -1;
    }

    @Override
    public int inBounds(MediaTrack track2) {
        if (format == null) {
            return -1;
        }

        // NOTE: MultiFpsFormat: 25/50, 30/60. Currently no more that 720p.
        boolean isMultiFpsFormat = sizeLessOrEquals(format.height, 720);

        // Detect preset by id presence
        boolean isPreset = format.id == null;

        if (isPreset) {
            // Overcome non-standard aspect ratio by getting resolution label
            boolean respectPresetsFps = !sIsNoFpsPresetsEnabled ||
                    sizeEquals(format.height, TrackSelectorUtil.getOriginHeight(track2.format.height));
            //return compare(track2, COMPARE_TYPE_IN_BOUNDS_PRESET) : // EXPERIMENT: replaced multi fps with strict fps in presets
            return compare(track2, isMultiFpsFormat || respectPresetsFps ? COMPARE_TYPE_IN_BOUNDS_PRESET : COMPARE_TYPE_IN_BOUNDS_PRESET_NO_FPS);
        } else {
            return compare(track2, isMultiFpsFormat ? COMPARE_TYPE_IN_BOUNDS : COMPARE_TYPE_IN_BOUNDS_NO_FPS);
        }
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

        //// Proper non-widescreen (4:3) format handling.
        //// 4:3 example: https://www.youtube.com/watch?v=m8nsUcAwkj8&t=1042s
        //if (isWideScreen(format) && isWideScreen(track2.format)) {
        //    size1 = format.width;
        //    size2 = track2.format.width;
        //} else {
        //    size1 = format.height;
        //    size2 = track2.format.height;
        //}

        // MOD: Mimic official behavior (handle low res shorts etc)
        size1 = isWideScreenMod(format) ? format.height : format.width;
        size2 = isWideScreenMod(track2.format) ? track2.format.height : track2.format.width;

        String id1 = format.id;
        String id2 = track2.format.id;
        // Low fps (e.g. 8fps) on original track could break whole comparison
        float frameRate1 = format.frameRate < LOW_FPS_THRESHOLD ? 30 : format.frameRate;
        float frameRate2 = track2.format.frameRate < LOW_FPS_THRESHOLD ? 30 : track2.format.frameRate;
        String codecs1 = format.codecs;
        String codecs2 = track2.format.codecs;
        int bitrate1 = format.bitrate;
        int bitrate2 = track2.format.bitrate;

        int result;

        if (type == COMPARE_TYPE_IN_BOUNDS) {
            result = inBounds(id1, id2, size1, size2, frameRate1, frameRate2, codecs1, codecs2, bitrate1, bitrate2);
        } else if (type == COMPARE_TYPE_IN_BOUNDS_NO_FPS) {
            result = inBounds(id1, id2, size1, size2, -1, -1, codecs1, codecs2, bitrate1, bitrate2);
        } else if (type == COMPARE_TYPE_IN_BOUNDS_PRESET) {
            result = inBoundsPreset(id1, id2, size1, size2, frameRate1, frameRate2, codecs1, codecs2);
        } else if (type == COMPARE_TYPE_IN_BOUNDS_PRESET_NO_FPS) {
            result = inBoundsPreset(id1, id2, size1, size2, -1, -1, codecs1, codecs2);
        } else {
            result = compare(id1, id2, size1, size2, frameRate1, frameRate2, codecs1, codecs2, bitrate1, bitrate2);
        }

        return result;
    }

    private int inBounds(String id1, String id2, int size1, int size2, float frameRate1, float frameRate2, String codecs1, String codecs2, int bitrate1, int bitrate2) {
        int result = -1;

        // Fix same id between normal videos and shorts
        if (Helpers.equals(id1, id2) && (size1 == size2)) {
            result = 0;
        //} else if (sizeLessOrEquals(size2, size1) && fpsLessOrEquals(frameRate2, frameRate1) && bitrateLessOrEquals(bitrate2, bitrate1)) {
        } else if (sizeLessOrEquals(size2, size1) && fpsLessOrEquals(frameRate2, frameRate1)) { // NOTE: Removed bitrate check to fix shorts?
            if (TrackSelectorUtil.isHdrCodec(codecs1) == TrackSelectorUtil.isHdrCodec(codecs2)) {
                result = 1;
            } else if (TrackSelectorUtil.isHdrCodec(codecs1)) {
                result = 1;
            }
        }

        return result;
    }

    private int inBoundsPreset(String id1, String id2, int size1, int size2, float frameRate1, float frameRate2, String codecs1, String codecs2) {
        // Fix same id between normal videos and shorts
        if (Helpers.equals(id1, id2) && (size1 == size2)) {
            return 0;
        }

        if (!TrackSelectorUtil.isHdrCodec(codecs1) && TrackSelectorUtil.isHdrCodec(codecs2)) {
            return -1;
        }

        if (fpsLess(frameRate1, frameRate2)) {
            return -1;
        }

        if (sizeLess(size1, size2)) {
            return -1;
        }

        return 1;
    }

    //private int inBoundsPreset(String id1, String id2, int size1, int size2, float frameRate1, float frameRate2, String codecs1, String codecs2) {
    //    int result = -1;
    //
    //    if (Helpers.equals(id1, id2)) {
    //        result = 0;
    //    } else if (sizeEquals(size1, size2)) {
    //        if (fpsEquals(frameRate2, frameRate1)) {
    //            if (TrackSelectorUtil.isHdrCodec(codecs1) == TrackSelectorUtil.isHdrCodec(codecs2)) {
    //                result = 0;
    //            } else if (TrackSelectorUtil.isHdrCodec(codecs1)) {
    //                result = 1;
    //            }
    //        } else if (fpsLessOrEquals(frameRate2, frameRate1)) {
    //            if (TrackSelectorUtil.isHdrCodec(codecs1) == TrackSelectorUtil.isHdrCodec(codecs2)) {
    //                result = 1;
    //            } else if (TrackSelectorUtil.isHdrCodec(codecs1)) {
    //                result = 1;
    //            }
    //        }
    //    } else if (sizeLessOrEquals(size2, size1) && fpsLessOrEquals(frameRate2, frameRate1)) {
    //        if (TrackSelectorUtil.isHdrCodec(codecs1) == TrackSelectorUtil.isHdrCodec(codecs2)) {
    //            result = 1;
    //        } else if (TrackSelectorUtil.isHdrCodec(codecs1)) {
    //            result = 1;
    //        }
    //    }
    //
    //    return result;
    //}

    private int compare(String id1, String id2, int size1, int size2, float frameRate1, float frameRate2, String codecs1, String codecs2, int bitrate1, int bitrate2) {
        if (Helpers.equals(id1, id2)) {
            return 0;
        }

        int leftScore = 0;
        int rightScore = 0;

        if (TrackSelectorUtil.isHdrCodec(codecs1) && !TrackSelectorUtil.isHdrCodec(codecs2)) {
            leftScore += 3;
        } else if (TrackSelectorUtil.isHdrCodec(codecs2) && !TrackSelectorUtil.isHdrCodec(codecs1)) {
            rightScore += 3;
        }

        if (fpsLess(frameRate1, frameRate2)) {
            rightScore += 2;
        } else if (fpsLess(frameRate2, frameRate1)) {
            leftScore += 2;
        }

        if (sizeLess(size1, size2)) {
            rightScore += 1;
        } else if (sizeLess(size2, size1)) {
            leftScore += 1;
        }

        int result = leftScore - rightScore;
        return result == 0 && TrackSelectorUtil.codecNameShort(codecs1).equals(TrackSelectorUtil.codecNameShort(codecs2)) ? bitrate1 - bitrate2 : result;
    }

    //private int compare(String id1, String id2, int size1, int size2, float frameRate1, float frameRate2, String codecs1, String codecs2) {
    //    int result = -1;
    //
    //    if (Helpers.equals(id1, id2)) {
    //        result = 0;
    //    } else if (sizeLessOrEquals(size2, size1)) {
    //        if (fpsLessOrEquals(frameRate2, frameRate1)) {
    //            if (TrackSelectorUtil.isHdrCodec(codecs1) == TrackSelectorUtil.isHdrCodec(codecs2)) {
    //                result = 0;
    //            } else if (TrackSelectorUtil.isHdrCodec(codecs2)) {
    //                result = -1;
    //            } else {
    //                result = 1;
    //            }
    //        }
    //    }
    //
    //    return result;
    //}

    /**
     * Check widescreen: 16:9, 16:8, 16:7 etc<br/>
     */
    private boolean isWideScreen(Format format) {
        if (format == null) {
            return false;
        }

        return format.width / (float) format.height >= 1.77;
    }

    /**
     * MOD: Mimic official behavior (handle low res shorts etc)
     */
    private boolean isWideScreenMod(Format format) {
        if (format == null) {
            return false;
        }

        return format.width / (float) format.height > 1;
    }
}
