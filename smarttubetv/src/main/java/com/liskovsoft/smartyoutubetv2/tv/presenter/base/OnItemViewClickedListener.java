package com.liskovsoft.smartyoutubetv2.tv.presenter.base;

import androidx.leanback.widget.Presenter;

public interface OnItemViewClickedListener {
    void onItemViewClicked(Presenter.ViewHolder itemViewHolder, Object item);
}
