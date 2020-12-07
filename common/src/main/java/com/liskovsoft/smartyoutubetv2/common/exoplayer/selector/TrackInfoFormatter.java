package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import com.google.android.exoplayer2.Format;

public class TrackInfoFormatter {
    public static String formatQualityLabel(Format format) {
        if (format == null) {
            return "none";
        }

        String separator = "/";

        String resolution = extractResolutionLabel(format);

        int fpsNum = extractFps(format);
        String fps = fpsNum == 0 ? "" : String.valueOf(fpsNum);

        String codec = TrackSelectorUtil.extractCodec(format);

        boolean isHdr = TrackSelectorUtil.isHdrCodec(format.codecs);
        String hdrStr = "";

        String qualityString;

        if (!fps.isEmpty()) {
            fps = separator + fps;
        }

        if (!codec.isEmpty()) {
            codec = separator + codec.toUpperCase();
        }

        if (isHdr) {
            hdrStr = separator + "HDR";
        }

        qualityString = String.format("%s%s%s%s", resolution, fps, codec, hdrStr);

        return qualityString;
    }

    private static String extractResolutionLabel(Format format) {
        String qualityLabel = "";

        if (format.width <= 854) { // 854x480
            qualityLabel = "SD";
        } else if (format.width <= 1280) { // 1280x720
            qualityLabel = "HD";
        } else if (format.width <= 1920) { // 1920x1080
            qualityLabel = "FHD";
        } else if (format.width <= 2560) { // 2560x1440
            qualityLabel = "QHD";
        } else if (format.width <= 3840) { // 3840x2160
            qualityLabel = "4K";
        } else if (format.width <= 7680) { // 7680x4320
            qualityLabel = "8K";
        }

        return qualityLabel;
    }

    private static int extractFps(Format format) {
        return format.frameRate == Format.NO_VALUE ? 0 : Math.round(format.frameRate);
    }
}
