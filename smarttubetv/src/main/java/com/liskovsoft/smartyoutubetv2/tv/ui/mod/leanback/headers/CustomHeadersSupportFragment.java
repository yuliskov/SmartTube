package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.headers;

import android.view.View;
import android.view.View.OnLayoutChangeListener;
import androidx.leanback.app.HeadersSupportFragment;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowHeaderPresenter;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;

public class CustomHeadersSupportFragment extends HeadersSupportFragment {
    public CustomHeadersSupportFragment() {
        Helpers.setField(this, "mAdapterListener", mCustomAdapterListener);
    }

    private final ItemBridgeAdapter.AdapterListener mCustomAdapterListener =
            new ItemBridgeAdapter.AdapterListener() {
                @Override
                public void onCreate(final ItemBridgeAdapter.ViewHolder viewHolder) {
                    OnHeaderClickedListener customOnHeaderClickedListener = (OnHeaderClickedListener) Helpers.getField(CustomHeadersSupportFragment.this, "mOnHeaderClickedListener");
                    ItemBridgeAdapter.Wrapper customWrapper = (ItemBridgeAdapter.Wrapper) Helpers.getField(CustomHeadersSupportFragment.this, "mWrapper");
                    OnLayoutChangeListener customLayoutChangeListener = (OnLayoutChangeListener) Helpers.getField(CustomHeadersSupportFragment.this, "sLayoutChangeListener");

                    View headerView = viewHolder.getViewHolder().view;
                    headerView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (customOnHeaderClickedListener != null) {
                                customOnHeaderClickedListener.onHeaderClicked(
                                        (RowHeaderPresenter.ViewHolder) viewHolder.getViewHolder(),
                                        (Row) viewHolder.getItem());
                            }
                        }
                    });

                    // NEW CODE
                    // Fix buggy G20s menu item hanging
                    headerView.setOnKeyListener((v, keyCode, event) -> false);

                    if (customWrapper != null) {
                        viewHolder.itemView.addOnLayoutChangeListener(customLayoutChangeListener);
                    } else {
                        headerView.addOnLayoutChangeListener(customLayoutChangeListener);
                    }
                }

            };
}
