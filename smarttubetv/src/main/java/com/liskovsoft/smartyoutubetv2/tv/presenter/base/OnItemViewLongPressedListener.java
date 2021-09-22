package com.liskovsoft.smartyoutubetv2.tv.presenter.base;

import androidx.leanback.widget.Presenter;

public interface OnItemViewLongPressedListener {
    void onItemLongPressed(Presenter.ViewHolder itemViewHolder, Object item);
}
