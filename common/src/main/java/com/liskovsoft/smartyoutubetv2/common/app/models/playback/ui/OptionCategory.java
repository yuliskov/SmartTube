package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OptionCategory {
    public static OptionCategory radioList(String title, List<OptionItem> items) {
        return new OptionCategory(title, items, TYPE_RADIO_LIST);
    }

    public static OptionCategory checkedList(String title, List<OptionItem> items) {
        return new OptionCategory(title, items, TYPE_CHECKBOX_LIST);
    }

    public static OptionCategory stringList(String title, List<OptionItem> items) {
        return new OptionCategory(title, items, TYPE_STRING_LIST);
    }

    public static OptionCategory longText(String title, OptionItem item) {
        return new OptionCategory(title, Collections.singletonList(item), TYPE_LONG_TEXT);
    }

    public static OptionCategory chat(String title, OptionItem item) {
        return new OptionCategory(title, Collections.singletonList(item), TYPE_CHAT);
    }

    public static OptionCategory comments(String title, OptionItem item) {
        return new OptionCategory(title, Collections.singletonList(item), TYPE_COMMENTS);
    }

    public static OptionCategory singleSwitch(OptionItem item) {
        ArrayList<OptionItem> items = new ArrayList<>();
        items.add(item);
        return new OptionCategory(null, items, TYPE_SINGLE_SWITCH);
    }

    public static OptionCategory singleButton(OptionItem item) {
        ArrayList<OptionItem> items = new ArrayList<>();
        items.add(item);
        return new OptionCategory(null, items, TYPE_SINGLE_BUTTON);
    }

    public static OptionCategory from(int id, int type, String title, List<OptionItem> options) {
        return new OptionCategory(title, options, type, id);
    }

    public static OptionCategory from(int id, int type, String title, OptionItem option) {
        return new OptionCategory(title, Collections.singletonList(option), type, id);
    }

    private OptionCategory(String title, List<OptionItem> options, int type) {
        this(title, options, type, -1);
    }

    private OptionCategory(String title, List<OptionItem> options, int type, int id) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.options = options;
    }

    public static final int TYPE_RADIO_LIST = 0;
    public static final int TYPE_CHECKBOX_LIST = 1;
    public static final int TYPE_SINGLE_SWITCH = 2;
    public static final int TYPE_SINGLE_BUTTON = 3;
    public static final int TYPE_STRING_LIST = 4;
    public static final int TYPE_LONG_TEXT = 5;
    public static final int TYPE_CHAT = 6;
    public static final int TYPE_COMMENTS = 7;
    public final int id;
    public final int type;
    public final String title;
    public final List<OptionItem> options;
}
