package com.liskovsoft.smartyoutubetv2.common.app.models.data;

public class Header {
    private int mId;
    private String mTitle;
    private final int mType;
    public static final int TYPE_GRID = 0;
    public static final int TYPE_ROW = 1;
    private final int mResId;

    public Header(int id, String title) {
        this(id, title, TYPE_ROW);
    }

    public Header(int id, String title, int type) {
        this(id, title, type, -1);
    }

    public Header(int id, String title, int type, int resId) {
        mId = id;
        mTitle = title;
        mType = type;
        mResId = resId;
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

    public int getResId() {
        return mResId;
    }
}
