package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.app.HeadersSupportFragment;
import androidx.leanback.app.HeadersSupportFragment.OnHeaderClickedListener;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowHeaderPresenter;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;

public class CustomBrowseSupportFragment extends BrowseSupportFragment {
    private static final String TAG = CustomBrowseSupportFragment.class.getSimpleName();
    private OnHeaderClickedListener mCustomOnHeaderClickedListener;
    private ItemBridgeAdapter.Wrapper mCustomWrapper;
    private OnLayoutChangeListener sCustomLayoutChangeListener;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        HeadersSupportFragment customHeadersSupportFragment = (HeadersSupportFragment) Helpers.getField(this, "mHeadersSupportFragment");

        if (customHeadersSupportFragment != null) {
            mCustomOnHeaderClickedListener = (OnHeaderClickedListener) Helpers.getField(customHeadersSupportFragment, "mOnHeaderClickedListener");
            mCustomWrapper = (ItemBridgeAdapter.Wrapper) Helpers.getField(customHeadersSupportFragment, "mWrapper");
            sCustomLayoutChangeListener = (OnLayoutChangeListener) Helpers.getField(customHeadersSupportFragment, "sLayoutChangeListener");
            Helpers.setField(customHeadersSupportFragment, "mAdapterListener", mCustomAdapterListener);
        }

        return view;
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
                            Log.d(TAG, "onClick");
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
