package com.liskovsoft.smartyoutubetv2.tv.presenter;

import androidx.leanback.widget.VerticalGridPresenter;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class CustomVerticalGridPresenter extends VerticalGridPresenter {
    public CustomVerticalGridPresenter() {
        super(ViewUtil.FOCUS_ZOOM_FACTOR, ViewUtil.USE_FOCUS_DIMMER);
    }
}
