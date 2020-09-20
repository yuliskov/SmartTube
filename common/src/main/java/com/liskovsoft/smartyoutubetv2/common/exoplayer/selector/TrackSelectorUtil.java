package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import android.text.TextUtils;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.MimeTypes;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager.MediaTrack;

public class TrackSelectorUtil {
    public static final String CODEC_SHORT_AVC = "avc";
    public static final String CODEC_SHORT_VP9 = "vp9";
    public static final String CODEC_SHORT_VP9_HDR = "vp9.2";
    public static final String CODEC_SHORT_MP4A = "mp4a";
    public static final String CODEC_SHORT_VORBIS = "vorbis";
    private static final String SEPARATOR = ", ";
    private static final int HEIGHT_EQUITY_THRESHOLD_PX = 80;

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

        return codec.equals("vp9.2");
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

    public static boolean heightEquals(int height1, int height2) {
        if (height1 == -1 || height2 == -1) {
            return false;
        }

        return Math.abs(height1 - height2) < HEIGHT_EQUITY_THRESHOLD_PX;
    }

    public static boolean heightLessOrEquals(int height1, int height2) {
        if (height1 == -1 || height2 == -1) {
            return false;
        }

        return height1 <= height2 || Math.abs(height1 - height2) < HEIGHT_EQUITY_THRESHOLD_PX;
    }

    public static boolean codecEquals(String codecs1, String codecs2) {
        if (codecs1 == null || codecs2 == null) {
            return false;
        }

        return Helpers.equals(codecNameShort(codecs1), codecNameShort(codecs2));
    }

    public static boolean fpsEquals(float fps1, float fps2) {
        if (fps1 == -1 || fps2 == -1) {
            return false;
        }

        return Math.abs(fps1 - fps2) < 10;
    }

    public static boolean compare(MediaTrack track1, MediaTrack track2) {
        boolean result = false;

        if (Helpers.equals(track1.format.id, track2.format.id)) {
            result = true;
        } else if (TrackSelectorUtil.codecEquals(track1.format.codecs, track2.format.codecs)) {
            if (TrackSelectorUtil.fpsEquals(track1.format.frameRate, track2.format.frameRate)) {
                if (TrackSelectorUtil.heightLessOrEquals(track1.format.height, track2.format.height)) {
                    result = true;
                }
            }
        }

        return result;
    }
}
