package com.liskovsoft.smartyoutubetv2.tv.ui.browse.video;

import android.os.Bundle;
import androidx.annotation.Nullable;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;

public class VideoRowsFragment extends MultipleRowsFragment {
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getMainFragmentAdapter().getFragmentHost() != null) {
            getMainFragmentAdapter().getFragmentHost().notifyDataReady(getMainFragmentAdapter());
        }
    }

    @Override
    protected VideoGroupPresenter getMainPresenter() {
        return BrowsePresenter.instance(getContext());
    }

    @Override
    public void setExpand(boolean expand) {
        // force expand by default
        super.setExpand(true);
    }
}
