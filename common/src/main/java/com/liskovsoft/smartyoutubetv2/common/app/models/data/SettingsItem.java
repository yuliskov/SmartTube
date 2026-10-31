package com.liskovsoft.smartyoutubetv2.common.app.models.data;

public class SettingsItem {
    public final String title;
    public final Runnable onClick;
    public int imageResId;

    public SettingsItem(String title, Runnable onClick) {
        this(title, onClick, -1);
    }

    public SettingsItem(String title, Runnable onClick, int imageResId) {
        this.title = title;
        this.onClick = onClick;
        this.imageResId = imageResId;
    }
}
