package com.liskovsoft.smartyoutubetv2.tv.ui.channelsub;

import android.os.Bundle;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelSubPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelSubView;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.video.VideoGridFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc.ProgressBarManager;

public class ChannelSubFragment extends VideoGridFragment implements ChannelSubView {
    private ProgressBarManager mProgressBarManager;
    private ChannelSubPresenter mPresenter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPresenter = ChannelSubPresenter.instance(getContext());
        mPresenter.register(this);

        mProgressBarManager = new ProgressBarManager();
    }

    @Override
    protected VideoGroupPresenter getMainPresenter() {
        return ChannelSubPresenter.instance(getContext());
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Don't move to onCreateView
        mProgressBarManager.setRootView((ViewGroup) getActivity().findViewById(android.R.id.content).getRootView());

        mPresenter.onInitDone();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mPresenter.unregister(this);
    }

    @Override
    public void showProgressBar(boolean show) {
        if (show) {
            mProgressBarManager.show();
        } else {
            mProgressBarManager.hide();
        }
    }
}
