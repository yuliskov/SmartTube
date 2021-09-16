package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import android.text.TextUtils;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.util.MimeTypes;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.VideoTrack;

import java.util.HashMap;

public class TrackSelectorUtil {
    public static final String CODEC_SHORT_AV1 = "av01";
    public static final String CODEC_SHORT_AVC = "avc";
    public static final String CODEC_SHORT_VP9 = "vp9";
    public static final String CODEC_SHORT_VP9_HDR = "vp9.2";
    public static final String CODEC_SHORT_MP4A = "mp4a";
    public static final String CODEC_SHORT_VORBIS = "vorbis";
    private static final String SEPARATOR = ", ";
    private static final HashMap<Integer, Integer> mResolutionMap = new HashMap<>();

    // Try to amplify resolution of aspect ratios that differ from 16:9
    static {
        mResolutionMap.put(256, 144);
        mResolutionMap.put(426, 240);
        mResolutionMap.put(640, 360);
        mResolutionMap.put(854, 480);
        mResolutionMap.put(1280, 720);
        mResolutionMap.put(1920, 1080);
        mResolutionMap.put(2048, 1440); // Tom Zanetti - Didn't Know
        mResolutionMap.put(2560, 1440);
        mResolutionMap.put(3120, 2160); // Мастерская Синдиката - Мы собрали суперкар КУВАЛДОЙ!
        mResolutionMap.put(3840, 2160);
        mResolutionMap.put(7680, 4320);
    }

    /**
     * Builds a track name for display.
     *
     * @param format {@link Format} of the track.
     * @return a generated name specific to the track.
     */
    public static CharSequence buildTrackNameShort(Format format) {
        String trackName;
        if (MimeTypes.isVideo(format.sampleMimeType)) {
            trackName = joinWithSeparator(joinWithSeparator(joinWithSeparator(joinWithSeparator(buildResolutionShortString(format),
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

    /**
     * Build short resolution: e.g. 720p, 1080p etc<br/>
     * Try to amplify resolution of aspect ratios that differ from 16:9
     */
    private static String buildResolutionShortString(Format format) {
        return getResolutionLabel(format) + "p";
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

        String[] codecNames = {CODEC_SHORT_AV1, CODEC_SHORT_AVC, CODEC_SHORT_VP9, CODEC_SHORT_MP4A, CODEC_SHORT_VORBIS};

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

    public static String stateToString(int playbackState) {
        return playbackState == Player.STATE_BUFFERING ? "STATE_BUFFERING" :
                playbackState == Player.STATE_READY ? "STATE_READY" :
                playbackState == Player.STATE_IDLE ? "STATE_IDLE" :
                "STATE_ENDED";
    }

    //public static int getResolutionLabel(Format format) {
    //    if (format == null) {
    //        return 0;
    //    }
    //
    //    int height = format.height;
    //    int width = format.width;
    //
    //    if (width == Format.NO_VALUE || height == Format.NO_VALUE) {
    //        return 0;
    //    }
    //
    //    // Try to amplify resolution of aspect ratios that differ from 16:9
    //    Integer heightAmp = mResolutionMap.get(width);
    //
    //    // Try to avoid square video proportions
    //    return heightAmp != null && width > height && !VideoTrack.sizeEquals(width, height, 15) ? heightAmp : height;
    //}

    public static String getResolutionLabel(Format format) {
        if (format == null) {
            return null;
        }

        int height = format.height;
        int width = format.width;

        if (width == Format.NO_VALUE || height == Format.NO_VALUE) {
            return null;
        }

        return getResolutionLabelByHeight(Math.min(height, width));
    }

    private static String getResolutionLabelByHeight(int height) {
        String qualityLabel = null;

        if (height < 160) { // 256x144
            qualityLabel = "144";
        } else if (height < 260) { // 426x240
            qualityLabel = "240";
        } else if (height < 380) { // 640x360
            qualityLabel = "360";
        } else if (height < 500) { // 854x480
            qualityLabel = "480";
        } else if (height < 750) { // 1280x720
            qualityLabel = "720";
        } else if (height < 1150) { // 1920x1080
            qualityLabel = "1080";
        } else if (height < 1250) { // 2560x1182 (Мастерская Синдиката - Мы собрали суперкар КУВАЛДОЙ!)
            qualityLabel = "1200";
        } else if (height < 1600) { // 2560x1440
            qualityLabel = "1440";
        } else if (height < 2300) { // 3840x2160
            qualityLabel = "2160";
        } else if (height < 4500) { // 7680x4320
            qualityLabel = "4320";
        }

        return qualityLabel;
    }
}
