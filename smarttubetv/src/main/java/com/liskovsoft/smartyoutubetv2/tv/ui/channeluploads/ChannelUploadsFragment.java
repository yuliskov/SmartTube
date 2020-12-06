package com.liskovsoft.smartyoutubetv2.tv.ui.channeluploads;

import android.os.Bundle;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelUploadsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.video.VideoGridFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc.ProgressBarManager;

public class ChannelUploadsFragment extends VideoGridFragment implements ChannelUploadsView {
    private ProgressBarManager mProgressBarManager;
    private ChannelUploadsPresenter mPresenter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPresenter = ChannelUploadsPresenter.instance(getContext());
        mPresenter.setView(this);

        mProgressBarManager = new ProgressBarManager();
    }

    @Override
    protected VideoGroupPresenter getMainPresenter() {
        return ChannelUploadsPresenter.instance(getContext());
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Don't move to onCreateView
        mProgressBarManager.setRootView((ViewGroup) getActivity().findViewById(android.R.id.content).getRootView());

        mPresenter.onViewInitialized();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mPresenter.onViewDestroyed();
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
