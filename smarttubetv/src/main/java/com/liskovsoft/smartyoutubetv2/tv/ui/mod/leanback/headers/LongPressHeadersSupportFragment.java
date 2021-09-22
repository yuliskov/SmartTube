package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.headers;

import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import androidx.leanback.app.HeadersSupportFragment;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowHeaderPresenter;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;

public class LongPressHeadersSupportFragment extends HeadersSupportFragment {
    public interface OnHeaderLongPressedListener {
        /**
         * Called when a header item has been clicked.
         *
         * @param viewHolder Row ViewHolder object corresponding to the selected Header.
         * @param row Row object corresponding to the selected Header.
         */
        void onHeaderLongPressed(RowHeaderPresenter.ViewHolder viewHolder, Row row);
    }

    private OnHeaderLongPressedListener mOnHeaderLongPressedListener;

    public LongPressHeadersSupportFragment() {
        Helpers.setField(this, "mAdapterListener", mCustomAdapterListener);
    }

    public void setOnHeaderLongPressedListener(OnHeaderLongPressedListener listener) {
        mOnHeaderLongPressedListener = listener;
    }

    private final ItemBridgeAdapter.AdapterListener mCustomAdapterListener =
            new ItemBridgeAdapter.AdapterListener() {
                @Override
                public void onCreate(final ItemBridgeAdapter.ViewHolder viewHolder) {
                    OnHeaderClickedListener customOnHeaderClickedListener = (OnHeaderClickedListener) Helpers.getField(LongPressHeadersSupportFragment.this, "mOnHeaderClickedListener");
                    ItemBridgeAdapter.Wrapper customWrapper = (ItemBridgeAdapter.Wrapper) Helpers.getField(LongPressHeadersSupportFragment.this, "mWrapper");
                    OnLayoutChangeListener customLayoutChangeListener = (OnLayoutChangeListener) Helpers.getField(LongPressHeadersSupportFragment.this, "sLayoutChangeListener");

                    View headerView = viewHolder.getViewHolder().view;
                    headerView.setOnClickListener(v -> {
                        if (customOnHeaderClickedListener != null) {
                            customOnHeaderClickedListener.onHeaderClicked(
                                    (RowHeaderPresenter.ViewHolder) viewHolder.getViewHolder(),
                                    (Row) viewHolder.getItem());
                        }
                    });

                    // NEW CODE
                    headerView.setOnLongClickListener(v -> {
                        if (mOnHeaderLongPressedListener != null) {
                            mOnHeaderLongPressedListener.onHeaderLongPressed(
                                    (RowHeaderPresenter.ViewHolder) viewHolder.getViewHolder(),
                                    (Row) viewHolder.getItem());
                            return true; // don't provoke single click event
                        }

                        return false; // work as usual
                    });

                    // NEW CODE
                    headerView.setOnKeyListener((v, keyCode, event) -> {
                        if (mOnHeaderLongPressedListener != null) {
                            if (KeyHelpers.isMenuKey(keyCode) && event.getAction() == KeyEvent.ACTION_DOWN) {
                                mOnHeaderLongPressedListener.onHeaderLongPressed(
                                        (RowHeaderPresenter.ViewHolder) viewHolder.getViewHolder(),
                                        (Row) viewHolder.getItem());
                            }
                        }

                        return false; // enable navigation events
                    });

                    if (customWrapper != null) {
                        viewHolder.itemView.addOnLayoutChangeListener(customLayoutChangeListener);
                    } else {
                        headerView.addOnLayoutChangeListener(customLayoutChangeListener);
                    }
                }

            };
}
