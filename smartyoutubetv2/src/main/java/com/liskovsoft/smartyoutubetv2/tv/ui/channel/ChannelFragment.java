package com.liskovsoft.smartyoutubetv2.tv.ui.channel;

import android.os.Bundle;
import androidx.annotation.Nullable;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.row.DynamicRowsFragment;

public class ChannelFragment extends DynamicRowsFragment implements ChannelView {
    private static final String TAG = ChannelFragment.class.getSimpleName();
    private ChannelPresenter mChannelPresenter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mChannelPresenter = ChannelPresenter.instance(getContext());
        mChannelPresenter.register(this);
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
}
