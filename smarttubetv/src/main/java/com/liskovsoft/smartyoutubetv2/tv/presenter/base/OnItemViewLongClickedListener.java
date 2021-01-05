package com.liskovsoft.smartyoutubetv2.tv.presenter.base;

import androidx.leanback.widget.Presenter;

public interface OnItemViewLongClickedListener {
    void onItemLongClicked(Presenter.ViewHolder itemViewHolder, Object item);
}
