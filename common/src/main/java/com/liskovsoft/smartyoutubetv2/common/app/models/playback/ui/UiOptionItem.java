package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;

import java.util.ArrayList;
import java.util.List;

public class UiOptionItem implements OptionItem {
    private int mId;
    private CharSequence mTitle;
    private CharSequence mDescription;
    private boolean mIsSelected;
    private FormatItem mFormat;

    public static List<OptionItem> from(List<FormatItem> formats) {
        return from(formats, null);
    }

    public static List<OptionItem> from(List<FormatItem> formats, String defaultTitle) {
        if (formats == null) {
            return null;
        }

        List<OptionItem> options = new ArrayList<>();

        for (FormatItem format : formats) {
            options.add(from(format, defaultTitle));
        }

        return options;
    }

    public static OptionItem from(FormatItem format) {
        return from(format, null);
    }

    public static OptionItem from(FormatItem format, String defaultTitle) {
        if (format == null) {
            return null;
        }

        UiOptionItem uiOptionItem = new UiOptionItem();

        uiOptionItem.mTitle = format.isDefault() ? defaultTitle : format.getTitle();
        uiOptionItem.mIsSelected = format.isSelected();
        uiOptionItem.mFormat = format;

        return uiOptionItem;
    }

    public static FormatItem toFormat(OptionItem option) {
        if (option instanceof UiOptionItem) {
            return ((UiOptionItem) option).mFormat;
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
    public CharSequence getDescription() {
        return mDescription;
    }

    @Override
    public boolean isSelected() {
        return mIsSelected;
    }
}
