package com.liskovsoft.smartyoutubetv2.tv.presenter.base;

import androidx.leanback.widget.Presenter;

public interface OnItemViewPressedListener {
    void onItemPressed(Presenter.ViewHolder itemViewHolder, Object item);
}
