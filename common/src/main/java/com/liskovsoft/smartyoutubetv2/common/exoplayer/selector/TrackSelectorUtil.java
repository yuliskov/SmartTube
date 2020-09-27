package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import android.text.TextUtils;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.MimeTypes;
import com.liskovsoft.sharedutils.helpers.Helpers;

public class TrackSelectorUtil {
    public static final String CODEC_SHORT_AVC = "avc";
    public static final String CODEC_SHORT_VP9 = "vp9";
    public static final String CODEC_SHORT_VP9_HDR = "vp9.2";
    public static final String CODEC_SHORT_MP4A = "mp4a";
    public static final String CODEC_SHORT_VORBIS = "vorbis";
    private static final String SEPARATOR = ", ";

    /**
     * Builds a track name for display.
     *
     * @param format {@link Format} of the track.
     * @return a generated name specific to the track.
     */
    public static CharSequence buildTrackNameShort(Format format) {
        String trackName;
        if (MimeTypes.isVideo(format.sampleMimeType)) {
            trackName = joinWithSeparator(joinWithSeparator(joinWithSeparator(joinWithSeparator(buildResolutionString(format),
                    buildFPSString(format)), buildBitrateString(format)), extractCodec(format)), buildHDRString(format));
        } else if (MimeTypes.isAudio(format.sampleMimeType)) {
            trackName = joinWithSeparator(joinWithSeparator(joinWithSeparator(joinWithSeparator(buildLanguageString(format),
                    buildAudioPropertyString(format)), buildBitrateString(format)), extractCodec(format)), buildChannels(format));
        } else {
            trackName = joinWithSeparator(joinWithSeparator(buildLanguageString(format), buildBitrateString(format)), extractCodec(format));
        }
        return trackName.length() == 0 ? "unknown" : trackName;
    }

    private static String buildHDRString(Format format) {
        if (format == null) {
            return "";
        }

        return isHdrCodec(format.codecs) ? "HDR" : "";
    }

    private static String buildFPSString(Format format) {
        return format.frameRate == Format.NO_VALUE ? "" : Helpers.formatFloat(format.frameRate) + "fps";
    }

    private static String buildResolutionString(Format format) {
        return format.width == Format.NO_VALUE || format.height == Format.NO_VALUE ? "" : format.height + "p";
    }

    private static String buildAudioPropertyString(Format format) {
        return format.channelCount == Format.NO_VALUE || format.sampleRate == Format.NO_VALUE ? "" :
                format.channelCount + "ch, " + format.sampleRate + "Hz";
    }

    private static String buildLanguageString(Format format) {
        return TextUtils.isEmpty(format.language) || "und".equals(format.language) ? "" : format.language;
    }

    private static String buildBitrateString(Format format) {
        double bitrateMB = Helpers.round(format.bitrate / 1_000_000f, 2);
        return format.bitrate == Format.NO_VALUE || bitrateMB == 0 ? "" : String.format("%sMbit", Helpers.formatFloat(bitrateMB));
    }

    private static String joinWithSeparator(String first, String second) {
        return first.length() == 0 ? second : (second.length() == 0 ? first : first + SEPARATOR + second);
    }

    /**
     * Add html color tag
     */
    private static String color(String input, String color) {
        return String.format("<font color=\"%s\">%s</font>", color, input);
    }

    public static boolean isHdrCodec(String codec) {
        if (codec == null) {
            return false;
        }

        return codec.equals(CODEC_SHORT_VP9_HDR);
    }

    public static String extractCodec(Format format) {
        if (format.codecs == null) {
            return "";
        }

        return codecNameShort(format.codecs);
    }

    public static String codecNameShort(String codecNameFull) {
        if (codecNameFull == null) {
            return null;
        }

        String codec = codecNameFull.toLowerCase();

        String[] codecNames = {CODEC_SHORT_AVC, CODEC_SHORT_VP9, CODEC_SHORT_MP4A, CODEC_SHORT_VORBIS};

        for (String codecName : codecNames) {
            if (codec.contains(codecName)) {
                return codecName;
            }
        }

        return codec;
    }

    private static String buildChannels(Format format) {
        return format.bitrate > 300000 ? "5.1" : "";
    }
}
