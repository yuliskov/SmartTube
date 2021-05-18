package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import androidx.leanback.widget.HeaderItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Category;

public class CategoryHeaderItem extends HeaderItem {
    private final Category mHeader;

    public CategoryHeaderItem(Category header) {
        super(header.getId(), header.getTitle());
        mHeader = header;
    }

    public int getType() {
        return mHeader.getType();
    }

    public int getResId() {
        return mHeader.getResId();
    }

    public String getIconUrl() {
        return mHeader.getIconUrl();
    }
}
