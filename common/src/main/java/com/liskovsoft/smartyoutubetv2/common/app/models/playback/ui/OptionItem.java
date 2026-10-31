package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

public interface OptionItem {
    int getId();
    CharSequence getTitle();
    CharSequence getDescription();
    boolean isSelected();
    void onSelect(boolean isSelected);
    Object getData();
    void setRequired(OptionItem... items);
    OptionItem[] getRequired();
    void setRadio(OptionItem... items);
    OptionItem[] getRadio();
    ChatReceiver getChatReceiver();
    CommentsReceiver getCommentsReceiver();
}
