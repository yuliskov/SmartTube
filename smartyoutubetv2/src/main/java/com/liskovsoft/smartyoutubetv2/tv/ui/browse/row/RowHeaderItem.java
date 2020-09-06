package com.liskovsoft.smartyoutubetv2.tv.ui.browse.row;

import androidx.leanback.widget.HeaderItem;

public class RowHeaderItem extends HeaderItem {
    public RowHeaderItem(long id, String name) {
        super(id, name);
    }

    public RowHeaderItem(String name) {
        super(name);
    }
}
