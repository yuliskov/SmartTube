package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.view.View;
import android.view.ViewGroup;

import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.SearchBar;

public class ChannelHeaderPresenter extends Presenter {
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        SearchBar searchBar = new SearchBar(parent.getContext());

        return new ViewHolder(searchBar);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        View view = viewHolder.view;
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {

    }
}
