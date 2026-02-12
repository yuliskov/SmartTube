package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.MimeTypes;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.SubtitleTrack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ExoFormatItem implements FormatItem {
    public static final int RESOLUTION_FHD = 3;
    public static final int RESOLUTION_HD = 2;
    public static final int RESOLUTION_SD = 1;
    public static final int RESOLUTION_LD = 0;
    public static final int FORMAT_AVC = 0;
    public static final int FORMAT_VP9 = 1;
    public static final int FORMAT_MP4A = 2;
    public static final int FPS_30 = 0;
    private int mType;
    private int mId;
    private CharSequence mTitle;
    private MediaTrack mTrack;
    private boolean mIsSelected;
    private boolean mIsDefault;
    private float mFrameRate;
    private int mWidth;
    private int mHeight;
    private String mCodecs;
    private String mLanguage;
    private String mFormatId;
    private boolean mIsPreset;

    public static List<FormatItem> from(Set<MediaTrack> mediaTracks) {
        if (mediaTracks == null) {
            return null;
        }

        List<FormatItem> formats = new ArrayList<>();

        for (MediaTrack track : mediaTracks) {
            formats.add(from(track));
        }

        return formats;
    }

    public static ExoFormatItem from(MediaTrack track, boolean isPreset) {
        if (track == null) {
            return null;
        }

        track.isPreset = isPreset;
        return from(track);
    }

    public static ExoFormatItem from(MediaTrack track) {
        if (track == null) {
            return null;
        }

        ExoFormatItem videoFormatItem = from(track.format);

        videoFormatItem.mType = track.rendererIndex;
        videoFormatItem.mIsSelected = track.isSelected;
        videoFormatItem.mTrack = track;
        videoFormatItem.mIsPreset = track.isPreset;

        return videoFormatItem;
    }

    public static ExoFormatItem from(Format format) {
        ExoFormatItem formatItem = new ExoFormatItem();

        if (format != null) {
            formatItem.mTitle = TrackSelectorUtil.buildTrackNameShort(format);
            // Workaround with LIVE streams. Where no fps info present.
            formatItem.mFrameRate = format.frameRate == -1 ? 30 : format.frameRate;
            formatItem.mWidth = format.width;
            formatItem.mHeight = format.height;
            formatItem.mCodecs = format.codecs;
            formatItem.mLanguage = format.language;
            formatItem.mType = getType(format);

            if (format.id != null) {
                formatItem.mId = format.id.hashCode();
                formatItem.mFormatId = format.id;
            }
        } else {
            formatItem.mIsDefault = true; // fake auto track
        }

        return formatItem;
    }

    private static int getType(Format format) {
        String sampleMimeType = format.sampleMimeType;

        return MimeTypes.isVideo(sampleMimeType) ? TYPE_VIDEO : MimeTypes.isAudio(sampleMimeType) ? TYPE_AUDIO : TYPE_SUBTITLE;
    }

    @NonNull
    @Override
    public String toString() {
        int rendererIndex = -1;
        String codecs = "";
        int width = -1;
        int height = -1;
        float frameRate = -1;
        String language = "";
        String id = "";
        boolean isPreset = false;
        int bitrate = -1;
        boolean isDrc = false;

        if (mTrack != null) {
            rendererIndex = mTrack.rendererIndex;
            isPreset = mTrack.isPreset;
            Format format = mTrack.format;
            if (format != null) {
                id = format.id;
                codecs = format.codecs;
                width = format.width;
                height = format.height;
                frameRate = format.frameRate;
                language = format.language;
                bitrate = format.bitrate;
                isDrc = format.isDrc;
            }
        }

        return String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s", mType, rendererIndex, id, codecs, width, height, frameRate, language, isPreset, bitrate, isDrc);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof ExoFormatItem) {
            ExoFormatItem formatItem = (ExoFormatItem) obj;

            switch (mType) {
                case TYPE_VIDEO:
                case TYPE_AUDIO:
                    // NOTE: Don't compare subs by formatId (it's non-constant)
                    if (mFormatId != null && formatItem.mFormatId != null) {
                        return mType == formatItem.mType &&
                                Helpers.equals(mFormatId, formatItem.mFormatId); // instead of compare by bitrate
                    }
                    return mIsPreset == formatItem.mIsPreset &&
                            mType == formatItem.mType &&
                            mFrameRate == formatItem.mFrameRate &&
                            mWidth == formatItem.mWidth &&
                            mHeight == formatItem.mHeight &&
                            Helpers.equals(mCodecs, formatItem.mCodecs) &&
                            Helpers.contains(SubtitleTrack.trim(mLanguage), SubtitleTrack.trim(formatItem.mLanguage));
                case TYPE_SUBTITLE:
                    return mType == formatItem.mType &&
                            Helpers.contains(SubtitleTrack.trim(mLanguage), SubtitleTrack.trim(formatItem.mLanguage));
            }
        }

        return super.equals(obj);
    }

    public static ExoFormatItem from(int type, int rendererIndex, String id, String codecs,
                                     int width, int height, float frameRate, String language,
                                     boolean isPreset, int bitrate, boolean isDrc) {
        MediaTrack mediaTrack = MediaTrack.forRendererIndex(rendererIndex);

        if (mediaTrack == null) {
            return null;
        }

        mediaTrack.isPreset = isPreset;

        // audio/video disabled
        if (TextUtils.isEmpty(id) && TextUtils.isEmpty(codecs)) {
            return from(mediaTrack);
        }

        // NOTE: Create fake format. It's used in app internal comparison routine.
        switch (type) {
            case TYPE_VIDEO:
                mediaTrack.format = Format.createVideoSampleFormat(
                        id, null, codecs, -1, -1, width, height,
                        frameRate, null, null);
                break;
            case TYPE_AUDIO:
                mediaTrack.format = Format.createAudioSampleFormat(
                        id, null, codecs, bitrate, -1,0, 0,
                        null, null, 0, language, isDrc);
                break;
            case TYPE_SUBTITLE:
                mediaTrack.format = Format.createTextSampleFormat(
                        id, null, -1, language);
                break;
        }

        return from(mediaTrack);
    }

    public static ExoFormatItem from(String spec) {
        if (spec == null) {
            return null;
        }

        String[] split = spec.split(",");

        if (split.length == 9) {
            split = Helpers.appendArray(split, new String[]{"-1"});
        } else if (split.length == 10) {
            split = Helpers.appendArray(split, new String[]{"false"});
        }

        if (split.length != 11) {
            return null;
        }

        int type = Helpers.parseInt(split[0]);
        int rendererIndex = Helpers.parseInt(split[1]);
        String id = Helpers.parseStr(split[2]);
        String codecs = Helpers.parseStr(split[3]);
        int width = Helpers.parseInt(split[4]);
        int height = Helpers.parseInt(split[5]);
        float frameRate = Helpers.parseFloat(split[6]);
        String language = Helpers.parseStr(split[7]);
        boolean isPreset = Helpers.parseBoolean(split[8]);
        int bitrate = Helpers.parseInt(split[9]);
        boolean isDrc = Helpers.parseBoolean(split[10]);

        return from(type, rendererIndex, id, codecs, width, height, frameRate, language, isPreset, bitrate, isDrc);
    }

    /**
     * "2560,1440,30,vp9"
     */
    public static ExoFormatItem fromVideoSpec(String spec, boolean isPreset) {
        if (spec == null) {
            return null;
        }

        String[] split = spec.split(",");

        if (split.length != 4) {
            return null;
        }

        int width = Helpers.parseInt(split[0]);
        int height = Helpers.parseInt(split[1]);
        float frameRate = Helpers.parseFloat(split[2]);
        String codec = split[3];

        return from(TYPE_VIDEO, TrackSelectorManager.RENDERER_INDEX_VIDEO, null, codec, width, height, frameRate,null, isPreset, -1, false);
    }

    public static FormatItem fromVideoParams(int resolution, int format, int frameRate) {
        ExoFormatItem formatItem = new ExoFormatItem();
        MediaTrack mediaTrack = MediaTrack.forRendererIndex(TrackSelectorManager.RENDERER_INDEX_VIDEO);
        formatItem.mTrack = mediaTrack;
        formatItem.mType = TYPE_VIDEO;

        int width = Integer.MAX_VALUE;
        int height = Integer.MAX_VALUE;
        String codec = null;
        int fps = 30;

        switch (resolution) {
            case RESOLUTION_FHD:
                width = 1920;
                height = 1080;
                break;
            case RESOLUTION_HD:
                width = 1280;
                height = 720;
                break;
            case RESOLUTION_SD:
                width = 640;
                height = 360;
                break;
            case RESOLUTION_LD:
                width = 426;
                height = 240;
                break;
        }

        switch (format) {
            case FORMAT_AVC:
                codec = "avc";
                break;
        }

        switch (frameRate) {
            case FPS_30:
                fps = 30;
                break;
        }

        // Fake format. It's used in app internal comparison routine.
        mediaTrack.format = Format.createVideoSampleFormat(
                null, null, codec, -1, -1, width, height, fps, null, null);

        return formatItem;
    }

    public static ExoFormatItem fromAudioData(int format) {
        ExoFormatItem formatItem = new ExoFormatItem();
        MediaTrack mediaTrack = MediaTrack.forRendererIndex(TrackSelectorManager.RENDERER_INDEX_AUDIO);
        formatItem.mTrack = mediaTrack;
        formatItem.mType = TYPE_AUDIO;

        String codec = null;

        switch (format) {
            case FORMAT_MP4A:
            default:
                codec = "mp4a";
                break;
        }

        // Fake format. It's used in app internal comparison routine.
        mediaTrack.format = Format.createAudioSampleFormat(
                null, null, codec, -1, -1,0, 0, null, null, 0, null);

        return formatItem;
    }

    /**
     * Codec and language (lower case) delimited by comma
     * @param spec codec, language
     */
    public static ExoFormatItem fromAudioSpecs(String spec) {
        if (spec == null) {
            return null;
        }

        String[] split = spec.split(",");

        if (split.length != 2) {
            return null;
        }

        String codec = Helpers.parseStr(split[0]);
        String language = Helpers.parseStr(split[1]);

        return from(TYPE_AUDIO, TrackSelectorManager.RENDERER_INDEX_AUDIO, null, codec, 0, 0, 0, language, false, -1, false);
    }

    public static FormatItem fromSubtitleParams(String langCode) {
        if (langCode != null) {
            // Only first part or lang code is accepted.
            // E.g.: en, ru...
            langCode = langCode.split("_")[0];
        }

        ExoFormatItem formatItem = new ExoFormatItem();
        MediaTrack mediaTrack = MediaTrack.forRendererIndex(TrackSelectorManager.RENDERER_INDEX_SUBTITLE);
        formatItem.mTrack = mediaTrack;
        formatItem.mType = TYPE_SUBTITLE;
        formatItem.mLanguage = langCode;
        formatItem.mIsDefault = langCode == null;

        mediaTrack.format = Format.createTextSampleFormat(null, null, -1, langCode);

        return formatItem;
    }

    @Override
    public int getId() {
        return mId;
    }

    @Override
    public CharSequence getTitle() {
        return mTitle;
    }

    @Override
    public boolean isSelected() {
        return mIsSelected;
    }

    @Override
    public boolean isDefault() {
        return mIsDefault;
    }

    @Override
    public float getFrameRate() {
        return mFrameRate;
    }

    @Override
    public String getLanguage() {
        return mLanguage;
    }

    @Override
    public int getWidth() {
        return mWidth;
    }

    @Override
    public int getHeight() {
        return mHeight;
    }

    @Override
    public int getType() {
        return mType;
    }

    @Override
    public boolean isPreset() {
        return mIsPreset;
    }

    @Override
    public MediaTrack getTrack() {
        return mTrack;
    }
}
