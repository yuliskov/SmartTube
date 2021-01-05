package com.liskovsoft.smartyoutubetv2.tv.presenter.base;

import android.view.KeyEvent;
import androidx.leanback.widget.Presenter;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;

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
                return true; // don't provoke single click event
            });

            //viewHolder.view.setOnKeyListener((v, keyCode, event) -> {
            //    if (event.getAction() == KeyEvent.ACTION_DOWN && KeyHelpers.isMenuKey(keyCode)) {
            //        if (mListener != null) {
            //            mListener.onItemLongClicked(viewHolder, item);
            //        }
            //    }
            //    return false; // enable navigation events
            //});
        }
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
        if (mListener != null) {
            viewHolder.view.setOnLongClickListener(null);
            //viewHolder.view.setOnKeyListener(null);
        }
    }
}
