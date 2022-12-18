package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

import java.util.List;

public class OptionCategory {
    public static final int TYPE_RADIO = 0;
    public static final int TYPE_CHECKED = 1;
    public static final int TYPE_SINGLE = 2;
    public static final int TYPE_STRING = 3;
    public static final int TYPE_LONG_TEXT = 4;
    public final int id;
    public final int type;
    public final String title;
    public final List<OptionItem> options;
    public final OptionItem option;

    private OptionCategory(int id, int type, String title, List<OptionItem> options) {
        this(id, type, title, options, null);
    }

    private OptionCategory(int id, String title, OptionItem option) {
        this(id, TYPE_SINGLE, title, null, option);
    }

    private OptionCategory(int id, int type, String title, List<OptionItem> options, OptionItem option) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.options = options;
        this.option = option;
    }

    public static OptionCategory from(int id, int type, String title, List<OptionItem> options) {
        return new OptionCategory(id, type, title, options);
    }

    public static OptionCategory from(int id, int type, String title, OptionItem option) {
        return new OptionCategory(id, type, title, null, option);
    }

    public static OptionCategory from(int id, OptionItem option) {
        return new OptionCategory(id, (String) option.getTitle(), option);
    }
}
