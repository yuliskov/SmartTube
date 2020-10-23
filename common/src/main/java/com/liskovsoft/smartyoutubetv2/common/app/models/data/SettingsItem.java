package com.liskovsoft.smartyoutubetv2.common.app.models.data;

public class SettingsItem {
    public final String title;
    public final Runnable onClick;

    public SettingsItem(String title, Runnable onClick) {
        this.title = title;
        this.onClick = onClick;
    }
}
