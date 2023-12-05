package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.leanback.widget.Presenter;

public class SearchBarPresenter extends Presenter {
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View contentView = inflater.inflate(com.liskovsoft.smartyoutubetv2.common.R.layout.simple_edit_dialog, null);

        return new ViewHolder(contentView);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {

    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {

    }

    public static class Data {
    }
}
