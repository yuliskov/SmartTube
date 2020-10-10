package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

import java.util.Map;

public interface OptionItem {
    int getId();
    CharSequence getTitle();
    CharSequence getDescription();
    boolean isSelected();
    void onSelect(boolean isSelected);
    Object getData();
    void setWhenUnselected(OptionItem... rules);
    OptionItem[] getWhenUnselected();
    void setWhenSelected(OptionItem... rules);
    OptionItem[] getWhenSelected();
}
