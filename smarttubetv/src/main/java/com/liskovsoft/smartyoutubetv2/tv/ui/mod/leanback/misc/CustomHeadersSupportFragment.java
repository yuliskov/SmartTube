package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc;

import android.view.View;
import android.view.View.OnLayoutChangeListener;
import androidx.leanback.app.HeadersSupportFragment;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowHeaderPresenter;
import com.liskovsoft.sharedutils.helpers.Helpers;

public class CustomHeadersSupportFragment extends HeadersSupportFragment {
    private final OnHeaderClickedListener mCustomOnHeaderClickedListener;
    private final ItemBridgeAdapter.Wrapper mCustomWrapper;
    private final OnLayoutChangeListener sCustomLayoutChangeListener;

    public CustomHeadersSupportFragment() {
        mCustomOnHeaderClickedListener = (OnHeaderClickedListener) Helpers.getField(this, "mOnHeaderClickedListener");
        mCustomWrapper = (ItemBridgeAdapter.Wrapper) Helpers.getField(this, "mWrapper");
        sCustomLayoutChangeListener = (OnLayoutChangeListener) Helpers.getField(this, "sLayoutChangeListener");
        Helpers.setField(this, "mAdapterListener", mCustomAdapterListener);
    }

    void updateAdapter() {
        ItemBridgeAdapter adapter = getBridgeAdapter();
        adapter.setAdapterListener(mCustomAdapterListener);
        adapter.setWrapper(mCustomWrapper);
    }

    private final ItemBridgeAdapter.AdapterListener mCustomAdapterListener =
            new ItemBridgeAdapter.AdapterListener() {
                @Override
                public void onCreate(final ItemBridgeAdapter.ViewHolder viewHolder) {
                    View headerView = viewHolder.getViewHolder().view;
                    headerView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (mCustomOnHeaderClickedListener != null) {
                                mCustomOnHeaderClickedListener.onHeaderClicked(
                                        (RowHeaderPresenter.ViewHolder) viewHolder.getViewHolder(),
                                        (Row) viewHolder.getItem());
                            }
                        }
                    });
                    if (mCustomWrapper != null) {
                        viewHolder.itemView.addOnLayoutChangeListener(sCustomLayoutChangeListener);
                    } else {
                        headerView.addOnLayoutChangeListener(sCustomLayoutChangeListener);
                    }
                }

            };
}
