package com.liskovsoft.smartyoutubetv2.common.exoplayer.managers.tracks;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.OptionItem;

public class VideoFormatItem implements OptionItem {
    private int mId;
    private int mType;
    private String mTitle;
    private String mDescription;

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
}
