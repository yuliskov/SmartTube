package com.liskovsoft.smartyoutubetv2.common.autoframerate;

public interface FormatItem {
    int getId();
    CharSequence getTitle();
    float getFrameRate();
    int getWidth();
    int getHeight();
    boolean isSelected();
}
