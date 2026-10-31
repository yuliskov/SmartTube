package com.liskovsoft.smartyoutubetv2.tv.presenter.base;

import android.view.KeyEvent;
import androidx.leanback.widget.Presenter;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;

public abstract class LongClickPresenter extends Presenter {
    private OnItemLongPressedListener mLongPressedListener;
    private Boolean mLongPressDisabled;

    public void setOnItemViewLongPressedListener(OnItemLongPressedListener listener) {
        mLongPressedListener = listener;
    }

    /**
     * Don't forget to call this method in descendants!
     */
    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        if (mLongPressDisabled == null) {
            mLongPressDisabled = GeneralData.instance(viewHolder.view.getContext()).isOkButtonLongPressDisabled();
        }

        viewHolder.view.setOnLongClickListener(v -> {
            if (mLongPressedListener != null && !mLongPressDisabled) {
                mLongPressedListener.onItemLongPressed(viewHolder, item);

                return true; // don't provoke single click event
            }

            return false; // work as usual
        });

        viewHolder.view.setOnKeyListener((v, keyCode, event) -> {
            if (mLongPressedListener != null) {
                if (KeyHelpers.isMenuKey(keyCode)) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        mLongPressedListener.onItemLongPressed(viewHolder, item);
                    }
                }
            }

            return false; // enable navigation events
        });
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
        if (mLongPressedListener != null) {
            viewHolder.view.setOnLongClickListener(null);
        }

        if (mLongPressedListener != null) {
            viewHolder.view.setOnKeyListener(null);
        }
    }
}
