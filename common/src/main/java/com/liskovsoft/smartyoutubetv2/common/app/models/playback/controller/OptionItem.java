package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller;

public interface OptionItem {
    int getType();
    int getId();
    CharSequence getTitle();
    CharSequence getDescription();
    boolean isSelected();
}
