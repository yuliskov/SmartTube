package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import com.google.android.exoplayer2.Format;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager.MediaTrack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class FormatOptionItem implements OptionItem {
    private int mId;
    private int mType;
    private CharSequence mTitle;
    private CharSequence mDescription;
    private MediaTrack mTrack;
    private boolean mIsSelected;

    public static List<OptionItem> from(Set<MediaTrack> mediaTracks) {
        return from(mediaTracks, null);
    }

    public static List<OptionItem> from(Set<MediaTrack> mediaTracks, String defaultTitle) {
        if (mediaTracks == null) {
            return null;
        }

        List<OptionItem> formats = new ArrayList<>();

        for (MediaTrack track : mediaTracks) {
            formats.add(from(track, defaultTitle));
        }

        return formats;
    }

    public static OptionItem from(MediaTrack track) {
        return from(track, null);
    }

    public static OptionItem from(MediaTrack track, String defaultTitle) {
        if (track == null) {
            return null;
        }

        FormatOptionItem videoFormatItem = new FormatOptionItem();

        Format format = track.format;

        if (format != null) {
            videoFormatItem.mTitle = TrackSelectorUtil.buildTrackNameShort(format);
        } else {
            videoFormatItem.mTitle = defaultTitle;
        }

        videoFormatItem.mIsSelected = track.isSelected;
        videoFormatItem.mTrack = track;

        return videoFormatItem;
    }

    public static MediaTrack toMediaTrack(OptionItem option) {
        if (option instanceof FormatOptionItem) {
            return ((FormatOptionItem) option).mTrack;
        }

        return null;
    }

    private static String createTitle(Object... args) {
        StringBuilder sb = new StringBuilder();

        for (Object arg : args) {
            if (sb.length() != 0) {
                sb.append(", ");
            }

            sb.append(arg);
        }

        return sb.toString();
    }

    @Override
    public int getType() {
        return mType;
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
    public CharSequence getDescription() {
        return mDescription;
    }

    @Override
    public boolean isSelected() {
        return mIsSelected;
    }
}
