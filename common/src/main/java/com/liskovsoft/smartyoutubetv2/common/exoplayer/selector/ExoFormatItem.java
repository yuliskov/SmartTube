package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import com.google.android.exoplayer2.Format;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager.MediaTrack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ExoFormatItem implements FormatItem {
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
}
