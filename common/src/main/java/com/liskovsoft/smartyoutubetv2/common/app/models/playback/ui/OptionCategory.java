package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

import java.util.List;

public class OptionCategory {
    public final int id;
    public final String title;
    public final List<OptionItem> options;
    public final OptionItem option;

    private OptionCategory(int id, String title, List<OptionItem> options) {
        this(id, title, options, null);
    }

    private OptionCategory(int id, String title, OptionItem option) {
        this(id, title, null, option);
    }

    private OptionCategory(int id, String title, List<OptionItem> options, OptionItem option) {
        this.id = id;
        this.title = title;
        this.options = options;
        this.option = option;
    }

    public static OptionCategory from(int id, String title, List<OptionItem> options) {
        return new OptionCategory(id, title, options);
    }

    public static OptionCategory from(int id, OptionItem option) {
        return new OptionCategory(id, (String) option.getTitle(), option);
    }
}
