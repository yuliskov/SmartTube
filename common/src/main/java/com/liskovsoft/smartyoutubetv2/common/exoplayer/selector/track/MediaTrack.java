package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelection.Definition;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorUtil;

public abstract class MediaTrack {
    private static final int VP9_WEIGHT = 31;
    private static final int AVC_WEIGHT = 28;
    private static final int AV1_WEIGHT = 14;
    private static int sVP9Weight = VP9_WEIGHT;
    private static int sAVCWeight = AVC_WEIGHT;
    private static int sAV1Weight = AV1_WEIGHT;
    public Format format;
    public int groupIndex = -1;
    public int trackIndex = -1;
    public boolean isSelected;
    public boolean isSaved;
    public boolean isPreset;
    public int rendererIndex;

    public MediaTrack(int rendererIndex) {
        this.rendererIndex = rendererIndex;
    }

    public static MediaTrack from(int rendererIndex, TrackGroupArray groups, Definition definition) {
        MediaTrack mediaTrack = forRendererIndex(rendererIndex);

        if (mediaTrack == null || groups == null || definition == null || definition.tracks == null) {
            return null;
        }

        mediaTrack.groupIndex = groups.indexOf(definition.group);
        mediaTrack.trackIndex = definition.tracks[0];

        return mediaTrack;
    }

    public abstract int compare(MediaTrack track2);
    public abstract int inBounds(MediaTrack track2);

    public static MediaTrack forRendererIndex(int rendererIndex) {
        switch (rendererIndex) {
            case TrackSelectorManager.RENDERER_INDEX_VIDEO:
                return new VideoTrack(rendererIndex);
            case TrackSelectorManager.RENDERER_INDEX_AUDIO:
                return new AudioTrack(rendererIndex);
            case TrackSelectorManager.RENDERER_INDEX_SUBTITLE:
                return new SubtitleTrack(rendererIndex);
        }

        return null;
    }

    protected static boolean bitrateLessOrEquals(int bitrate1, int bitrate2) {
        if (bitrate1 == -1 || bitrate2 == -1) {
            return true;
        }

        return bitrate1 <= bitrate2;
    }

    private static boolean codecEquals(String codecs1, String codecs2) {
        if (codecs1 == null || codecs2 == null) {
            return false;
        }

        return Helpers.equals(TrackSelectorUtil.codecNameShort(codecs1), TrackSelectorUtil.codecNameShort(codecs2));
    }

    private static boolean codecEquals(Format format1, Format format2) {
        if (format1 == null || format2 == null) {
            return false;
        }

        return codecEquals(format1.codecs, format2.codecs);
    }

    private static boolean bitrateEquals(Format format1, Format format2) {
        if (format1 == null || format2 == null) {
            return false;
        }

        return format1.bitrate == format2.bitrate;
    }

    private static boolean preferByBitrate(Format format1, Format format2) {
        if (format1 == null) {
            return false;
        }

        if (format2 == null) {
            return true;
        }

        if (!codecEquals(format1, format2)) {
            return true;
        }

        return format1.bitrate > format2.bitrate;
    }

    public static boolean codecEquals(MediaTrack track1, MediaTrack track2) {
        if (track1 == null || track2 == null) {
            return false;
        }

        return codecEquals(track1.format, track2.format);
    }

    public static boolean bitrateEquals(MediaTrack track1, MediaTrack track2) {
        if (track1 == null || track2 == null) {
            return false;
        }

        return bitrateEquals(track1.format, track2.format);
    }

    public static boolean preferByBitrate(MediaTrack track1, MediaTrack track2) {
        if (track1 == null || track2 == null) {
            return false;
        }

        return preferByBitrate(track1.format, track2.format);
    }

    public static int getCodecWeight(MediaTrack track) {
        if (track == null || track.format == null) {
            return 0;
        }

        return getCodecWeight(track.format.codecs);
    }

    public static int getCodecWeight(String codec) {
        return isVP9Codec(codec) ? sVP9Weight : isAVCCodec(codec) ? sAVCWeight : isAV1Codec(codec) ? sAV1Weight : 0;
    }

    public static boolean preferByCodec(MediaTrack prevTrack, MediaTrack nextTrack) {
        return getCodecWeight(prevTrack) - getCodecWeight(nextTrack) > 0;
    }

    public static void preferAvcOverVp9(boolean prefer) {
        sAVCWeight = prefer ? VP9_WEIGHT : AVC_WEIGHT;
        sVP9Weight = prefer ? AVC_WEIGHT : VP9_WEIGHT;
    }

    public boolean isVP9Codec() {
        return format != null && isVP9Codec(format.codecs);
    }

    public boolean isAV1Codec() {
        return format != null && isAV1Codec(format.codecs);
    }

    public boolean isMP4ACodec() {
        return format != null && isMP4ACodec(format.codecs);
    }

    public boolean isEmpty() {
        return groupIndex == -1 && trackIndex == -1;
    }

    public int getWidth() {
        return format != null ? format.width : -1;
    }

    public int getHeight() {
        return format != null ? format.height : -1;
    }

    private static boolean isVP9Codec(String codec) {
        if (codec == null) {
            return false;
        }

        codec = codec.toLowerCase();

        return Helpers.containsAny(codec, "vp9", "vp09");
    }

    private static boolean isAVCCodec(String codec) {
        if (codec == null) {
            return false;
        }

        codec = codec.toLowerCase();

        return codec.contains("avc");
    }

    private static boolean isAV1Codec(String codec) {
        if (codec == null) {
            return false;
        }

        codec = codec.toLowerCase();

        return codec.contains("av01");
    }

    private static boolean isMP4ACodec(String codec) {
        if (codec == null) {
            return false;
        }

        codec = codec.toLowerCase();

        return codec.contains("mp4a");
    }
}
