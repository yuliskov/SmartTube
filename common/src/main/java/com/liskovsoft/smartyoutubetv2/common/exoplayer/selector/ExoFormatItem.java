package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.MimeTypes;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager.MediaTrack;

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

    public static FormatItem from(MediaTrack track) {
        if (track == null) {
            return null;
        }

        ExoFormatItem videoFormatItem = new ExoFormatItem();

        Format format = track.format;

        if (format != null) {
            videoFormatItem.mTitle = TrackSelectorUtil.buildTrackNameShort(format);
            videoFormatItem.mFrameRate = format.frameRate;
            videoFormatItem.mHeight = format.height;
            videoFormatItem.mWidth = format.width;

            if (format.id != null) {
                videoFormatItem.mId = format.id.hashCode();
            }
        } else {
            videoFormatItem.mIsDefault = true; // fake auto track
        }

        videoFormatItem.mType = track.rendererIndex;
        videoFormatItem.mIsSelected = track.isSelected;
        videoFormatItem.mTrack = track;

        return videoFormatItem;
    }

    public static MediaTrack toMediaTrack(FormatItem option) {
        if (option instanceof ExoFormatItem) {
            return ((ExoFormatItem) option).mTrack;
        }

        return null;
    }

    public static FormatItem from(Format format) {
        ExoFormatItem formatItem = new ExoFormatItem();
        formatItem.mFrameRate = format.frameRate;
        formatItem.mWidth = format.width;
        formatItem.mHeight = format.height;
        formatItem.mTitle = TrackSelectorUtil.buildTrackNameShort(format);
        formatItem.mType = getType(format);

        return formatItem;
    }

    private static int getType(Format format) {
        String sampleMimeType = format.sampleMimeType;

        return MimeTypes.isVideo(sampleMimeType) ? TYPE_VIDEO : MimeTypes.isAudio(sampleMimeType) ? TYPE_AUDIO : TYPE_SUBTITLE;
    }

    public static FormatItem createFakeVideoFormat(int resolution, int format, int frameRate) {
        ExoFormatItem formatItem = new ExoFormatItem();
        MediaTrack mediaTrack = new MediaTrack();
        formatItem.mTrack = mediaTrack;
        formatItem.mType = TYPE_VIDEO;

        mediaTrack.rendererIndex = TrackSelectorManager.RENDERER_INDEX_VIDEO;

        int width = -1;
        int height = -1;
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

    public static FormatItem createFakeAudioFormat(int format) {
        ExoFormatItem formatItem = new ExoFormatItem();
        MediaTrack mediaTrack = new MediaTrack();
        formatItem.mTrack = mediaTrack;
        formatItem.mType = TYPE_AUDIO;

        mediaTrack.rendererIndex = TrackSelectorManager.RENDERER_INDEX_AUDIO;

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
}
