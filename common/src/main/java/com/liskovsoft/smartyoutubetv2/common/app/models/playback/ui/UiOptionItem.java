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
    private OptionCallback mCallback;
    private Object mData;
    private OptionItem[] mCheckedRules;

    public static List<OptionItem> from(List<FormatItem> formats, OptionCallback callback) {
        return from(formats, callback, null);
    }

    public static List<OptionItem> from(List<FormatItem> formats, OptionCallback callback, String defaultTitle) {
        if (formats == null) {
            return null;
        }

        List<OptionItem> options = new ArrayList<>();

        for (FormatItem format : formats) {
            options.add(from(format, callback, defaultTitle));
        }

        return options;
    }

    public static OptionItem from(FormatItem format, OptionCallback callback) {
        return from(format, callback, null);
    }

    public static OptionItem from(FormatItem format, OptionCallback callback, String defaultTitle) {
        if (format == null) {
            return null;
        }

        UiOptionItem uiOptionItem = new UiOptionItem();

        uiOptionItem.mTitle = format.isDefault() ? defaultTitle : format.getTitle();
        uiOptionItem.mIsSelected = format.isSelected();
        uiOptionItem.mFormat = format;
        uiOptionItem.mCallback = callback;

        return uiOptionItem;
    }

    public static OptionItem from(String title, OptionCallback callback) {
        return from(title, callback, false);
    }

    public static OptionItem from(String title, OptionCallback callback, boolean isChecked) {
        return from(title, callback, isChecked, null);
    }

    public static OptionItem from(String title, OptionCallback callback, boolean isChecked, Object data) {
        UiOptionItem uiOptionItem = new UiOptionItem();

        uiOptionItem.mTitle = title;
        uiOptionItem.mIsSelected = isChecked;
        uiOptionItem.mCallback = callback;
        uiOptionItem.mData = data;

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

    @Override
    public void onSelect(boolean isSelected) {
        mIsSelected = isSelected;

        if (mCallback != null) {
            mCallback.onSelect(this);
        }
    }

    @Override
    public Object getData() {
        return mData;
    }

    @Override
    public void setRequire(OptionItem... rules) {
        if (rules == null || rules.length == 0) {
            mCheckedRules = null;
        }

        mCheckedRules = rules;
    }

    @Override
    public OptionItem[] getRequire() {
        return mCheckedRules;
    }
}
