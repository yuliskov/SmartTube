package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.view.View;
import android.view.ViewGroup;

import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.SearchBar;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class ChannelSearchRowPresenter extends RowPresenter {
    @Override
    protected ViewHolder createRowViewHolder(ViewGroup parent) {
        SearchBar searchBar = new SearchBar(parent.getContext());

        setSelectEffectEnabled(ViewUtil.ROW_SELECT_EFFECT_ENABLED);

        return new ViewHolder(searchBar);
    }

    @Override
    protected void onBindRowViewHolder(ViewHolder vh, Object item) {
        super.onBindRowViewHolder(vh, item);

        //View searchBar = vh.view;
        //int padding = (int) searchBar.getContext().getResources().getDimension(R.dimen.lb_browse_item_vertical_spacing);
        //searchBar.setPadding(searchBar.getPaddingLeft(), searchBar.getPaddingTop(), searchBar.getPaddingRight(), padding);
    }

    public static class Data extends Row {
        
    }
}
