package com.liskovsoft.smartyoutubetv2.tv.presenter.base;

import android.view.KeyEvent;
import androidx.leanback.widget.Presenter;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;

public abstract class CustomEventsPresenter extends Presenter {
    private OnItemViewPressedListener mLongPressedListener;
    private OnItemViewPressedListener mMenuPressedListener;

    public void setOnItemViewLongPressedListener(OnItemViewPressedListener listener) {
        mLongPressedListener = listener;
    }

    public void setOnItemViewMenuPressedListener(OnItemViewPressedListener listener) {
        mMenuPressedListener = listener;
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        if (mLongPressedListener != null) {
            viewHolder.view.setOnLongClickListener(v -> {
                if (mLongPressedListener != null) {
                    mLongPressedListener.onItemPressed(viewHolder, item);

                    return true; // don't provoke single click event
                }

                return false; // work as usual
            });
        }

        if (mMenuPressedListener != null) {
            viewHolder.view.setOnKeyListener((v, keyCode, event) -> {
                if (mMenuPressedListener != null) {
                    if (KeyHelpers.isMenuKey(keyCode)) {
                        if (event.getAction() == KeyEvent.ACTION_DOWN) {
                            mMenuPressedListener.onItemPressed(viewHolder, item);
                        }
                    }
                }

                return false; // enable navigation events
            });
        }
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
        if (mLongPressedListener != null) {
            viewHolder.view.setOnLongClickListener(null);
        }

        if (mMenuPressedListener != null) {
            viewHolder.view.setOnKeyListener(null);
        }
    }
}
