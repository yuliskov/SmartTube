package com.liskovsoft.smartyoutubetv2.tv.presenter.base;

import androidx.leanback.widget.Presenter;

public interface OnItemLongPressedListener {
    void onItemLongPressed(Presenter.ViewHolder itemViewHolder, Object item);
}
