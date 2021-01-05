package com.liskovsoft.smartyoutubetv2.tv.presenter.base;

import androidx.leanback.widget.Presenter;

public abstract class LongClickPresenter extends Presenter {
    private OnItemViewLongClickedListener mListener;

    public void setOnItemViewLongClickedListener(OnItemViewLongClickedListener listener) {
        mListener = listener;
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        if (mListener != null) {
            viewHolder.view.setOnLongClickListener(v -> {
                if (mListener != null) {
                    mListener.onItemLongClicked(viewHolder, item);
                }
                return true;
            });
        }
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
        if (mListener != null) {
            viewHolder.view.setOnLongClickListener(null);
        }
    }
}
