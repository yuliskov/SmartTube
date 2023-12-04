package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.view.ViewGroup;

import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.SearchBar;

public class ChannelSearchRowPresenter extends RowPresenter {
    @Override
    protected ViewHolder createRowViewHolder(ViewGroup parent) {
        SearchBar searchBar = new SearchBar(parent.getContext());

        return new ViewHolder(searchBar);
    }

    @Override
    protected void onBindRowViewHolder(ViewHolder vh, Object item) {
        super.onBindRowViewHolder(vh, item);
    }

    public static class Data extends Row {
        
    }
}
