package com.liskovsoft.smartyoutubetv2.tv.presenter;

import androidx.leanback.widget.ListRowPresenter;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class CustomListRowPresenter extends ListRowPresenter {
    public CustomListRowPresenter() {
        this(ViewUtil.FOCUS_ZOOM_FACTOR, ViewUtil.FOCUS_DIMMER_ENABLED);
    }

    public CustomListRowPresenter(int focusZoomFactor, boolean useFocusDimmer) {
        super(focusZoomFactor, useFocusDimmer);
        setSelectEffectEnabled(ViewUtil.ROW_SELECT_EFFECT_ENABLED);
    }
}
