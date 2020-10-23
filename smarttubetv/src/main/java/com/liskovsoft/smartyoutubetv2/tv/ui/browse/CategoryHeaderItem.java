package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import androidx.leanback.widget.HeaderItem;

public class CategoryHeaderItem extends HeaderItem {
    private int mType = -1;
    private int mResId = -1;

    public CategoryHeaderItem(long id, String name, int type, int resId) {
        super(id, name);
        mType = type;
        mResId = resId;
    }

    public CategoryHeaderItem(long id, String name) {
        super(id, name);
    }

    public CategoryHeaderItem(String name) {
        super(name);
    }

    public int getType() {
        return mType;
    }

    public int getResId() {
        return mResId;
    }
}
