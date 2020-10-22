package com.liskovsoft.smartyoutubetv2.common.app.models.data;

public class Header {
    public static final int TYPE_GRID = 0;
    public static final int TYPE_ROW = 1;
    public static final int TYPE_TEXT_GRID = 2;
    private final int mId;
    private final String mTitle;
    private final int mType;
    private final int mResId;
    private final boolean mIsAuthOnly;

    public Header(int id, String title, int type, int resId) {
        this(id, title, type, resId, false);
    }

    public Header(int id, String title, int type, int resId, boolean isAuthOnly) {
        mId = id;
        mTitle = title;
        mType = type;
        mResId = resId;
        mIsAuthOnly = isAuthOnly;
    }

    public String getTitle() {
        return mTitle;
    }

    public int getId() {
        return mId;
    }

    public int getType() {
        return mType;
    }

    public int getResId() {
        return mResId;
    }

    public boolean isAuthOnly() {
        return mIsAuthOnly;
    }
}
