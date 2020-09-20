package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

public interface OptionItem {
    int getId();
    CharSequence getTitle();
    CharSequence getDescription();
    boolean isSelected();
    void onSelect(boolean isSelected);
}
