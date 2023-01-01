package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;

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
    private OptionItem[] mRequiredItems;
    private OptionItem[] mRadioItems;
    private ChatReceiver mChatReceiver;
    private CommentsReceiver mCommentsReceiver;

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

    public static OptionItem from(CharSequence title) {
        return from(title, (OptionCallback) null);
    }

    public static OptionItem from(CharSequence title, OptionCallback callback) {
        return from(title, callback, false);
    }

    public static OptionItem from(CharSequence title, OptionCallback callback, boolean isChecked) {
        return from(title, callback, isChecked, null);
    }

    public static OptionItem from(CharSequence title, CharSequence description, OptionCallback callback, boolean isChecked) {
        return from(title, description, callback, isChecked, null);
    }

    public static OptionItem from(CharSequence title, OptionCallback callback, boolean isChecked, Object data) {
        return from(title, null, callback, isChecked, data);
    }

    public static OptionItem from(CharSequence title, CharSequence description, OptionCallback callback, boolean isChecked, Object data) {
        UiOptionItem uiOptionItem = new UiOptionItem();

        uiOptionItem.mTitle = title;
        uiOptionItem.mDescription = description;
        uiOptionItem.mIsSelected = isChecked;
        uiOptionItem.mCallback = callback;
        uiOptionItem.mData = data;

        return uiOptionItem;
    }

    public static OptionItem from(CharSequence title, ChatReceiver chatReceiver) {
        UiOptionItem uiOptionItem = new UiOptionItem();
        uiOptionItem.mTitle = title;
        uiOptionItem.mChatReceiver = chatReceiver;

        return uiOptionItem;
    }

    public static OptionItem from(CharSequence title, CommentsReceiver commentsReceiver) {
        UiOptionItem uiOptionItem = new UiOptionItem();
        uiOptionItem.mTitle = title;
        uiOptionItem.mCommentsReceiver = commentsReceiver;

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
    public void setRequired(OptionItem... items) {
        if (items == null || items.length == 0) {
            mRequiredItems = null;
        }

        mRequiredItems = items;
    }

    @Override
    public OptionItem[] getRequired() {
        return mRequiredItems;
    }

    @Override
    public void setRadio(OptionItem... items) {
        if (items == null || items.length == 0) {
            mRadioItems = null;
        }

        mRadioItems = items;
    }

    @Override
    public OptionItem[] getRadio() {
        return mRadioItems;
    }

    @Override
    public ChatReceiver getChatReceiver() {
        return mChatReceiver;
    }

    @Override
    public CommentsReceiver getCommentsReceiver() {
        return mCommentsReceiver;
    }
}
