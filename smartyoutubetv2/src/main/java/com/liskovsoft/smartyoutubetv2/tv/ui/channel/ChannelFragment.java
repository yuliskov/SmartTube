package com.liskovsoft.smartyoutubetv2.tv.ui.channel;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import androidx.leanback.app.ProgressBarManager;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.row.DynamicRowsFragment;

public class ChannelFragment extends DynamicRowsFragment implements ChannelView {
    private static final String TAG = ChannelFragment.class.getSimpleName();
    private ChannelPresenter mChannelPresenter;
    private ProgressBarManager mProgressBarManager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mChannelPresenter = ChannelPresenter.instance(getContext());
        mChannelPresenter.register(this);

        mProgressBarManager = new ProgressBarManager();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = super.onCreateView(inflater, container, savedInstanceState);

        mProgressBarManager.setRootView((ViewGroup)root);

        return root;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mChannelPresenter.onInitDone();
    }

    @Override
    protected VideoGroupPresenter<?> getMainPresenter() {
        return ChannelPresenter.instance(getContext());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mChannelPresenter.unregister(this);
    }

    @Override
    public void showProgressBar(boolean show) {
        //if (show) {
        //    mProgressBarManager.enableProgressBar();
        //    mProgressBarManager.show();
        //} else {
        //    mProgressBarManager.hide();
        //}
    }
}
