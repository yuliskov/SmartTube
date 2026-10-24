package com.liskovsoft.smartyoutubetv2.tv.presenter;

import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ListRowPresenter;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class CustomListRowPresenter extends ListRowPresenter {
    public CustomListRowPresenter() {
        super(ViewUtil.FOCUS_ZOOM_FACTOR, ViewUtil.FOCUS_DIMMER_ENABLED);
        setSelectEffectEnabled(ViewUtil.ROW_SELECT_EFFECT_ENABLED);
    }

    // MOD: keep the focused card at the row start (YouTube TV style) instead of scrolling it to the middle
    @Override
    protected void initializeRowViewHolder(RowPresenter.ViewHolder holder) {
        super.initializeRowViewHolder(holder);
        HorizontalGridView grid = ((ListRowPresenter.ViewHolder) holder).getGridView();
        grid.setWindowAlignment(HorizontalGridView.WINDOW_ALIGN_NO_EDGE);
        grid.setWindowAlignmentOffset(0);
        grid.setWindowAlignmentOffsetPercent(HorizontalGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);
        grid.setItemAlignmentOffset(0);
        grid.setItemAlignmentOffsetPercent(HorizontalGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED);
    }
}
