package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;

import java.util.List;

public class UiOptionItem implements OptionItem {
    public static List<OptionItem> from(List<FormatItem> formats) {
        return null;
    }

    public static FormatItem toFormat(OptionItem option) {
        return null;
    }

    @Override
    public int getType() {
        return 0;
    }

    @Override
    public int getId() {
        return 0;
    }

    @Override
    public CharSequence getTitle() {
        return null;
    }

    @Override
    public CharSequence getDescription() {
        return null;
    }

    @Override
    public boolean isSelected() {
        return false;
    }
}
