package com.liskovsoft.smartyoutubetv2.tv.presenter;

import androidx.leanback.widget.ListRowPresenter;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class CustomListRowPresenter extends ListRowPresenter {
    public CustomListRowPresenter() {
        super(ViewUtil.FOCUS_ZOOM_FACTOR, ViewUtil.USE_FOCUS_DIMMER);
        setSelectEffectEnabled(ViewUtil.SELECT_EFFECT_ENABLED);
    }
}
