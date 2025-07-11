package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import android.text.TextUtils;
import android.util.Pair;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.util.MimeTypes;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.SubtitleTrack;

import java.util.HashMap;

public class TrackSelectorUtil {
    private static final String CODEC_PREFIX_AV1 = "av01";
    private static final String CODEC_PREFIX_AVC = "avc";
    private static final String CODEC_PREFIX_VP9 = "vp9";
    private static final String CODEC_PREFIX_VP09 = "vp09";
    private static final String CODEC_PREFIX_MP4A = "mp4a";
    private static final String CODEC_PREFIX_VORBIS = "vorbis";
    private static final String CODEC_PREFIX_VP9_HDR = "vp9.2";
    private static final String CODEC_SUFFIX_AV1_HDR = "10.0.110.09.18.09.0";
    private static final String CODEC_SUFFIX_AV1_HDR2 = "10.0.110.09.16.09.0";
    private static final String CODEC_SHORT_AV1 = "av1";
    private static final String HDR_PROFILE_ENDING = "hdr";
    private static final String SEPARATOR = ", ";
    private static final HashMap<Integer, Integer> mResolutionMap = new HashMap<>();
    // Unicode chars: https://symbl.cc/en/search/?q=mark
    public static final String HIGH_BITRATE_MARK = "\uD83D\uDC8E"; // diamond

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
            trackName = joinWithSeparator(joinWithSeparator(joinWithSeparator(joinWithSeparator(joinWithSeparator(buildResolutionShortString(format),
                    buildFPSString(format)), buildBitrateString(format)), extractCodec(format)), buildHDRString(format)), buildHighBitrateMark(format));
        } else if (MimeTypes.isAudio(format.sampleMimeType)) {
            trackName = joinWithSeparator(joinWithSeparator(joinWithSeparator(joinWithSeparator(joinWithSeparator(buildLanguageString(format),
                    buildAudioPropertyString(format)), buildBitrateString(format)), extractCodec(format)), buildChannels(format)), buildDrcMark(format));
        } else if (MimeTypes.isText(format.sampleMimeType)) {
            trackName = buildLanguageString(format);
        } else {
            trackName = joinWithSeparator(joinWithSeparator(buildLanguageString(format), buildBitrateString(format)), extractCodec(format));
        }
        return trackName.length() == 0 ? "unknown" : trackName;
    }

    /**
     * Add high bitrate (Premium) mark
     */
    public static String buildHighBitrateMark(Format format) {
        // Unicode chars: https://symbl.cc/en/search/?q=mark
        return isHighBitrateFormat(format) ? HIGH_BITRATE_MARK : "";
    }

    public static boolean isHighBitrateFormat(Format format) {
        return format != null && format.containerMimeType == null && format.height >= 1080;
    }

    public static String buildHDRString(Format format) {
        if (format == null) {
            return "";
        }

        return isHdrFormat(format) ? "HDR" : "";
    }

    private static String buildFPSString(Format format) {
        return format.frameRate == Format.NO_VALUE ? "" : Helpers.formatFloat(format.frameRate) + "fps";
    }

    /**
     * Build short resolution: e.g. 720p, 1080p etc<br/>
     * Try to amplify resolution of aspect ratios that differ from 16:9
     */
    private static String buildResolutionShortString(Format format) {
        return getResolutionLabel(format);
    }

    private static String buildAudioPropertyString(Format format) {
        return format.channelCount == Format.NO_VALUE || format.sampleRate == Format.NO_VALUE ? "" :
                format.channelCount + "ch, " + format.sampleRate + "Hz";
    }

    private static String buildLanguageString(Format format) {
        return TextUtils.isEmpty(format.language) || "und".equals(format.language) ? "" : SubtitleTrack.trimIfAuto(format.language);
    }

    private static String buildBitrateString(Format format) {
        double bitrateMB = Helpers.round(format.bitrate / 1_000_000f, 2);
        return format.bitrate == Format.NO_VALUE || bitrateMB == 0 ? "" : String.format("%sMbps", Helpers.formatFloat(bitrateMB));
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

    public static boolean isHdrFormat(Format format) {
        if (format == null) {
            return false;
        }

        return isHdrFormat(format.id, format.codecs);
    }

    public static boolean isHdrFormat(String id, String codecs) {
        return id != null ? isHdrFormat(id) : isHdrCodec(codecs);
    }

    private static boolean isHdrCodec(String codec) {
        if (codec == null) {
            return false;
        }

        return codec.equals(CODEC_PREFIX_VP9_HDR) || Helpers.endsWithAny(codec, CODEC_SUFFIX_AV1_HDR, CODEC_SUFFIX_AV1_HDR2, HDR_PROFILE_ENDING);
    }

    private static boolean isHdrFormat(String id) {
        if (id == null) {
            return false;
        }

        // webm hdr range: 330-337
        // mp4 hdr range: 694-701

        int parsed = Helpers.parseInt(id);

        return (parsed >= 330 && parsed <= 337) || (parsed >= 694 && parsed <=701);
    }

    public static String extractCodec(Format format) {
        if (format.codecs == null) {
            return "";
        }

        return codecNameShort(format.codecs);
    }

    public static String extractBitrate(Format format, int places) {
        double bitrateMB = Helpers.round(format.bitrate / 1_000_000f, places);
        return format.bitrate == Format.NO_VALUE || bitrateMB == 0 ? "" : Helpers.formatFloat(bitrateMB);
    }

    public static String codecNameShort(String codecNameFull) {
        if (codecNameFull == null) {
            return null;
        }

        String codec = codecNameFull.toLowerCase();

        String[] codecNames = {CODEC_PREFIX_AV1, CODEC_PREFIX_AVC, CODEC_PREFIX_VP9, CODEC_PREFIX_VP09, CODEC_PREFIX_MP4A, CODEC_PREFIX_VORBIS};

        for (String codecName : codecNames) {
            if (codec.contains(codecName)) {
                return fixShortCodecName(codecName);
            }
        }

        return codec;
    }

    private static String fixShortCodecName(String shortCodecName) {
        if (shortCodecName == null) {
            return null;
        }

        switch (shortCodecName) {
            case CODEC_PREFIX_AV1:
                return CODEC_SHORT_AV1;
            case CODEC_PREFIX_VP09:
                return CODEC_PREFIX_VP9;
        }

        return shortCodecName;
    }

    public static String buildChannels(Format format) {
        return is51Audio(format) ? "5.1" : "";
    }

    public static String buildDrcMark(Format format) {
        return isDrc(format) ? "DRC" : "";
    }

    public static boolean isDrc(Format format) {
        return format != null && Helpers.endsWithAny(format.id, "drc");
    }

    public static boolean is51Audio(Format format) {
        if (format == null) {
            return false;
        }

        return format.bitrate > 300000;
    }

    public static boolean is48KAudio(Format format) {
        if (format == null) {
            return false;
        }

        return format.sampleRate >= 48000;
    }

    public static boolean isVideo(Format format) {
        return MimeTypes.isVideo(format.sampleMimeType);
    }

    public static boolean isAudio(Format format) {
        return MimeTypes.isAudio(format.sampleMimeType);
    }

    public static String stateToString(int playbackState) {
        return playbackState == Player.STATE_BUFFERING ? "STATE_BUFFERING" :
                playbackState == Player.STATE_READY ? "STATE_READY" :
                playbackState == Player.STATE_IDLE ? "STATE_IDLE" :
                "STATE_ENDED";
    }

    /**
     * Check widescreen: 16:9, 16:8, 16:7 etc<br/>
     */
    public static boolean isWideScreenOld(Format format) {
        if (format == null) {
            return false;
        }

        return format.width / (float) format.height >= 1.77;
    }

    /**
     * MOD: Mimic official behavior (handle low res shorts etc)
     */
    public static boolean isWideScreen(Format format) {
        if (format == null) {
            return false;
        }

        return format.width / (float) format.height > 1;
    }

    public static String getResolutionLabel(Format format) {
        Pair<String, String> labels = getResolutionPrefixAndHeight(format);

        if (labels == null) {
            return null;
        }

        String prefix = labels.first != null ? "(" + labels.first + ") " : "";

        return prefix + labels.second + "p";
    }

    public static String getShortResolutionLabel(Format format) {
        Pair<String, String> labels = getResolutionPrefixAndHeight(format);

        if (labels == null) {
            return null;
        }

        return labels.first != null ? labels.first : labels.second;
    }

    public static int getOriginHeight(int height) {
        int originHeight = height;

        // Non-regular examples
        // Мастерская Синдиката - Мы собрали суперкар КУВАЛДОЙ! - 2560x1182
        // [AMATORY] ALL STARS: LIVE IN MOSCOW 2021 - 2560x1088 

        if (height < 160) { // 256x144
            originHeight = 144;
        } else if (height < 260) { // 426x240
            originHeight = 240;
        } else if (height < 380) { // 640x360
            originHeight = 360;
        } else if (height < 500) { // 854x480
            originHeight = 480;
        } else if (height < 750) { // 1280x720
            originHeight = 720;
        } else if (height < 1085) { // 1920x1080
            originHeight = 1080;
        } else if (height < 1500) { // 2560x1440
            originHeight = 1440;
        } else if (height < 2200) { // 3840x2160
            originHeight = 2160;
        } else if (height < 4400) { // 7680x4320
            originHeight = 4320;
        }

        return originHeight;
    }

    public static int getHeightByWidth(int width) {
        int originHeight = -1;

        if (width < 280) { // 256x144
            originHeight = 144;
        } else if (width < 440) { // 426x240
            originHeight = 240;
        } else if (width < 650) { // 640x360
            originHeight = 360;
        } else if (width < 870) { // 854x480
            originHeight = 480;
        } else if (width < 1300) { // 1280x720
            originHeight = 720;
        } else if (width < 2000) { // 1920x1080
            originHeight = 1080;
        } else if (width < 2600) { // 2560x1440
            originHeight = 1440;
        } else if (width < 3900) { // 3840x2160
            originHeight = 2160;
        } else if (width < 7700) { // 7680x4320
            originHeight = 4320;
        }

        return originHeight;
    }

    /**
     * Get the height in terms like it's understandable by the codec.
     */
    public static int getRealHeight(Format format) {
        if (format == null) {
            return -1;
        }

        int height = format.height;
        int width = format.width;

        if (width == Format.NO_VALUE || height == Format.NO_VALUE) {
            return -1;
        }

        // Make resolution calculation of the vertical videos more closer to the official app.
        boolean isUltraWide = (float) width/height >= 2.1; // maybe 2.3???
        int originHeight = isUltraWide ? getHeightByWidth(width) : getOriginHeight(Math.min(height, width));

        // Ignore vertical videos completely. Only height matters.
        //int originHeight = getOriginHeight(height);

        return originHeight;
    }

    private static String getResolutionPrefix(int originHeight) {
        String prefix = null;

        if (originHeight == 1440) {
            prefix = "2K";
        } else if (originHeight == 2160) {
            prefix = "4K";
        } else if (originHeight == 4320) {
            prefix = "8K";
        }

        return prefix;
    }

    private static Pair<String, String> getResolutionPrefixAndHeight(Format format) {
        int originHeight = getRealHeight(format);

        if (originHeight == -1) {
            return null;
        }

        String prefix = getResolutionPrefix(originHeight);

        return new Pair<>(prefix, String.valueOf(originHeight));
    }
}
