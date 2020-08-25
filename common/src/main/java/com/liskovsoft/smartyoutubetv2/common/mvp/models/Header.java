package com.liskovsoft.smartyoutubetv2.common.mvp.models;

public class Header {
    private int mId;
    private String mTitle;

    public Header(int id, String title) {
        mId = id;
        mTitle = title;
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String title) {
        mTitle = title;
    }

    public int getId() {
        return mId;
    }

    public void setId(int id) {
        mId = id;
    }
}
