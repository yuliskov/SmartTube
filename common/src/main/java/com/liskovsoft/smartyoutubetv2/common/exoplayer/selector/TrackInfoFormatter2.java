package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import com.google.android.exoplayer2.Format;

public class TrackInfoFormatter2 {
    private String mResolutionStr;
    private String mFpsStr;
    private String mCodecStr;
    private String mBitrateStr;
    private String mHdrStr;
    private String mSpeedStr;
    private String mChannelsStr;

    public void setFormat(Format format) {
        if (TrackSelectorUtil.isVideo(format)) {
            setVideoFormat(format);
        } else if (TrackSelectorUtil.isAudio(format)) {
            setAudioFormat(format);
        }
    }

    public void setVideoFormat(Format format) {
        if (format == null) {
            return;
        }

        mResolutionStr = TrackSelectorUtil.getShortResolutionLabel(format);

        int fpsNum = extractFps(format);
        mFpsStr = fpsNum == 0 ? "" : String.valueOf(fpsNum);

        String codec = TrackSelectorUtil.extractCodec(format);
        mCodecStr = codec != null ? codec.toUpperCase() : "";

        String bitrate = TrackSelectorUtil.extractBitrate(format);
        mBitrateStr = bitrate.toUpperCase();

        mHdrStr = TrackSelectorUtil.buildHDRString(format);
    }

    public void setAudioFormat(Format format) {
        if (format == null) {
            return;
        }

        mChannelsStr = TrackSelectorUtil.buildChannels(format);
    }

    public void setSpeed(float speed) {
        mSpeedStr = speed != 1.0f ? speed + "x" : "";
    }

    public String getQualityLabel() {
        return combine(mResolutionStr, mFpsStr, mCodecStr, mHdrStr, mChannelsStr, mSpeedStr);
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

    private static int extractFps(Format format) {
        return format.frameRate == Format.NO_VALUE ? 0 : Math.round(format.frameRate);
    }
}
