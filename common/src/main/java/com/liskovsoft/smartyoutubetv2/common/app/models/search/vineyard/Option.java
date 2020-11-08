package com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard;

public class Option {
    public String title;
    public String value;
    public int iconResource;

    public Option(String title, String value, int iconResource) {
        this.title = title;
        this.value = value;
        this.iconResource = iconResource;
    }
}
