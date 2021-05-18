package com.liskovsoft.smartyoutubetv2.tv.presenter.base;

import android.view.KeyEvent;
import androidx.leanback.widget.Presenter;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;

public abstract class LongClickPresenter extends Presenter {
    private OnItemViewClickedListener mLongClickListener;
    private OnItemViewClickedListener mMenuPressListener;

    public void setOnLongClickedListener(OnItemViewClickedListener listener) {
        mLongClickListener = listener;
    }

    public void setOnMenuPressedListener(OnItemViewClickedListener listener) {
        mMenuPressListener = listener;
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        if (mLongClickListener != null) {
            viewHolder.view.setOnLongClickListener(v -> {
                if (mLongClickListener != null) {
                    mLongClickListener.onItemViewClicked(viewHolder, item);

                    return true; // don't provoke single click event
                }

                return false; // work as usual
            });
        }

        if (mMenuPressListener != null) {
            viewHolder.view.setOnKeyListener((v, keyCode, event) -> {
                if (mMenuPressListener != null) {
                    if (KeyHelpers.isMenuKey(keyCode)) {
                        if (event.getAction() == KeyEvent.ACTION_DOWN) {
                            mMenuPressListener.onItemViewClicked(viewHolder, item);
                        }
                    }
                }

                return false; // enable navigation events
            });
        }
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
        if (mLongClickListener != null) {
            viewHolder.view.setOnLongClickListener(null);
        }

        if (mMenuPressListener != null) {
            viewHolder.view.setOnKeyListener(null);
        }
    }
}
