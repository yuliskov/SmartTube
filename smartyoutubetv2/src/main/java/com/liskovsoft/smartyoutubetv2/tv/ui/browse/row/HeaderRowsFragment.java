package com.liskovsoft.smartyoutubetv2.tv.ui.browse.row;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;

public class HeaderRowsFragment extends DynamicRowsFragment {
    @Override
    protected VideoGroupPresenter<?> getMainPresenter() {
        return BrowsePresenter.instance(getContext());
    }
}
