package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import com.google.android.exoplayer2.Format;

public class TrackInfoFormatter2 {
    private String mResolutionStr;
    private String mFpsStr;
    private String mCodecStr;
    private String mHdrStr;
    private String mSpeedStr;

    public void setFormat(Format format) {
        if (format == null) {
            return;
        }

        mResolutionStr = TrackSelectorUtil.getResolutionLabel(format);

        int fpsNum = extractFps(format);
        mFpsStr = fpsNum == 0 ? "" : String.valueOf(fpsNum);

        String codec = TrackSelectorUtil.extractCodec(format);
        mCodecStr = codec != null ? codec.toUpperCase() : "";

        boolean isHdr = TrackSelectorUtil.isHdrCodec(format.codecs);
        mHdrStr = isHdr ? "HDR" : "";
    }

    public void setSpeed(float speed) {
        mSpeedStr = speed != 1.0f ? speed + "x" : "";
    }

    public String getQualityLabel() {
        return combine(mResolutionStr, mFpsStr, mCodecStr, mHdrStr, mSpeedStr);
    }

    private static String combine(String... items) {
        String separator = "/";
        StringBuilder result = new StringBuilder();

        if (items != null && items.length != 0) {
            int index = 0;

            for (String item : items) {
                if (item == null || item.isEmpty()) {
                    continue;
                }

                if (index != 0) {
                    result.append(separator);
                }

                result.append(item);
                index++;
            }
        }

        return result.toString();
    }

    //private static String extractResolutionLabel(Format format) {
    //    int width = format.width;
    //    int height = format.height;
    //
    //    // Try to avoid square video proportions
    //    return width > height && !VideoTrack.sizeEquals(width, height, 15) ? getResolutionLabelByWidth(width) : getResolutionLabelByHeight(height);
    //}

    //private static String getResolutionLabelByWidth(int width) {
    //    String qualityLabel = "";
    //
    //    if (width <= 854) { // 854x480
    //        qualityLabel = "SD";
    //    } else if (width <= 1280) { // 1280x720
    //        qualityLabel = "HD";
    //    } else if (width <= 1920) { // 1920x1080
    //        qualityLabel = "FHD";
    //    } else if (width <= 2560) { // 2560x1440
    //        qualityLabel = "QHD";
    //    } else if (width <= 3840) { // 3840x2160
    //        qualityLabel = "4K";
    //    } else if (width <= 7680) { // 7680x4320
    //        qualityLabel = "8K";
    //    }
    //
    //    return qualityLabel;
    //}

    //private static String getResolutionLabelByHeight(int height) {
    //    String qualityLabel = "";
    //
    //    if (height <= 480) { // 854x480
    //        qualityLabel = "SD";
    //    } else if (height <= 720) { // 1280x720
    //        qualityLabel = "HD";
    //    } else if (height <= 1080) { // 1920x1080
    //        qualityLabel = "FHD";
    //    } else if (height <= 1440) { // 2560x1440
    //        qualityLabel = "QHD";
    //    } else if (height <= 2160) { // 3840x2160
    //        qualityLabel = "4K";
    //    } else if (height <= 4320) { // 7680x4320
    //        qualityLabel = "8K";
    //    }
    //
    //    return qualityLabel;
    //}

    private static int extractFps(Format format) {
        return format.frameRate == Format.NO_VALUE ? 0 : Math.round(format.frameRate);
    }
}
