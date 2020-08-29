package com.liskovsoft.smartyoutubetv2.common.mvp.models;

public class Header {
    private int mId;
    private String mTitle;
    private final int mType;
    public static final int TYPE_GRID = 0;
    public static final int TYPE_ROW = 1;

    public Header(int id, String title) {
        this(id, title, TYPE_GRID);
    }

    public Header(int id, String title, int type) {
        mId = id;
        mTitle = title;
        mType = type;
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

    public int getType() {
        return mType;
    }
}
