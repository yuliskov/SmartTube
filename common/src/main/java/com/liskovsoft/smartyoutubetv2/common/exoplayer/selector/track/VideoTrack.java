package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorUtil;

public class VideoTrack extends MediaTrack {
    private static final int HEIGHT_EQUITY_THRESHOLD_PX = 80;

    public VideoTrack(int rendererIndex) {
        super(rendererIndex);
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

    private static boolean widthEquals(int width1, int width2) {
        if (width1 == -1 || width2 == -1) {
            return false;
        }

        return Math.abs(width1 - width2) < HEIGHT_EQUITY_THRESHOLD_PX;
    }

    private static boolean widthLessOrEquals(int width1, int width2) {
        if (width1 == -1 || width2 == -1) {
            return false;
        }

        return width1 <= width2 || widthEquals(width1, width2);
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

    //@Override
    //public int compare(MediaTrack track2) {
    //    if (track2.format == null) {
    //        return 1;
    //    }
    //
    //    int result = -1;
    //
    //    if (Helpers.equals(format.id, track2.format.id)) {
    //        result = 0;
    //    } if (fpsLessOrEquals(track2.format.frameRate, format.frameRate)) {
    //        if (heightEquals(format.height, track2.format.height)) {
    //            if (codecEquals(format.codecs, track2.format.codecs)) {
    //                if (TrackSelectorUtil.isHdrCodec(format.codecs) == TrackSelectorUtil.isHdrCodec(track2.format.codecs)) {
    //                    result = 0;
    //                } else {
    //                    result = 1;
    //                }
    //            } else {
    //                result = 1;
    //            }
    //        } else if (heightLessOrEquals(track2.format.height, format.height)) {
    //            result = 1;
    //        }
    //    }
    //
    //    return result;
    //}

    //@Override
    //public int compare(MediaTrack track2) {
    //    if (track2.format == null) {
    //        return 1;
    //    }
    //
    //    int result = -1;
    //
    //    if (Helpers.equals(format.id, track2.format.id)) {
    //        result = 0;
    //    } if (heightEquals(format.height, track2.format.height)) {
    //        if (codecEquals(format.codecs, track2.format.codecs)) {
    //            if (fpsLessOrEquals(track2.format.frameRate, format.frameRate)) {
    //                if (TrackSelectorUtil.isHdrCodec(format.codecs) == TrackSelectorUtil.isHdrCodec(track2.format.codecs)) {
    //                    result = 0;
    //                } else {
    //                    result = 1;
    //                }
    //            } else {
    //                result = 1;
    //            }
    //        } else {
    //            result = 1;
    //        }
    //    } else if (heightLessOrEquals(track2.format.height, format.height)) {
    //        result = 1;
    //    }
    //
    //    return result;
    //}

    //@Override
    //public int compare(MediaTrack track2) {
    //    if (track2.format == null) {
    //        return 1;
    //    }
    //
    //    int result = -1;
    //
    //    if (Helpers.equals(format.id, track2.format.id)) {
    //        result = 0;
    //    } if (heightEquals(format.height, track2.format.height)) {
    //        if (fpsLessOrEquals(track2.format.frameRate, format.frameRate)) {
    //            if (TrackSelectorUtil.isHdrCodec(format.codecs) == TrackSelectorUtil.isHdrCodec(track2.format.codecs)) {
    //                result = 0;
    //            } else {
    //                result = 1;
    //            }
    //        } else {
    //            result = 1;
    //        }
    //    } else if (heightLessOrEquals(track2.format.height, format.height)) {
    //        result = 1;
    //    }
    //
    //    return result;
    //}

    @Override
    public int inBounds(MediaTrack track2) {
        if (track2.format == null) {
            return 1;
        }

        int result = -1;

        if (Helpers.equals(format.id, track2.format.id)) {
            result = 0;
        } if (widthEquals(format.width, track2.format.width)) {
            if (fpsLessOrEquals(track2.format.frameRate, format.frameRate)) {
                if (TrackSelectorUtil.isHdrCodec(format.codecs) == TrackSelectorUtil.isHdrCodec(track2.format.codecs)) {
                    result = 0;
                } else {
                    result = 1;
                }
            } else {
                result = 1;
            }
        } else if (widthLessOrEquals(track2.format.width, format.width)) {
            result = 1;
        }

        return result;
    }

    @Override
    public int compare(MediaTrack track2) {
        if (track2.format == null) {
            return 1;
        }

        int result = -1;

        if (Helpers.equals(format.id, track2.format.id)) {
            result = 0;
        } if (widthLessOrEquals(track2.format.width, format.width)) {
            if (fpsLessOrEquals(track2.format.frameRate, format.frameRate)) {
                if (TrackSelectorUtil.isHdrCodec(format.codecs) == TrackSelectorUtil.isHdrCodec(track2.format.codecs)) {
                    result = 0;
                } else {
                    result = 1;
                }
            }
        }

        return result;
    }
}
