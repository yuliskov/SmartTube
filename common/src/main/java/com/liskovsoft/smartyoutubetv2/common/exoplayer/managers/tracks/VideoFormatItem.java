package com.liskovsoft.smartyoutubetv2.common.exoplayer.managers.tracks;

import com.google.android.exoplayer2.Format;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.managers.tracks.TrackSelectionManager.MediaTrack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class VideoFormatItem implements OptionItem {
    private int mId;
    private int mType;
    private String mTitle;
    private String mDescription;
    private MediaTrack mTrack;
    private boolean mIsSelected;

    public static List<OptionItem> from(Set<MediaTrack> mediaTracks) {
        List<OptionItem> formats = new ArrayList<>();

        for (MediaTrack track : mediaTracks) {
            formats.add(from(track));
        }

        return formats;
    }

    private static OptionItem from(MediaTrack track) {
        VideoFormatItem videoFormatItem = new VideoFormatItem();

        Format format = track.format;

        if (format != null) {
            videoFormatItem.mTitle = createTitle(
                    format.height, format.frameRate, format.containerMimeType);
        } else {
            videoFormatItem.mTitle = "Auto";
        }

        videoFormatItem.mIsSelected = track.isSelected;
        videoFormatItem.mTrack = track;

        return videoFormatItem;
    }

    public static MediaTrack toMediaTrack(OptionItem option) {
        if (option instanceof VideoFormatItem) {
            return ((VideoFormatItem) option).mTrack;
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
    public String getTitle() {
        return mTitle;
    }

    @Override
    public String getDescription() {
        return mDescription;
    }

    @Override
    public boolean isSelected() {
        return mIsSelected;
    }
}
