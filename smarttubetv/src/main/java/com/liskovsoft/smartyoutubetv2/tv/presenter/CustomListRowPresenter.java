package com.liskovsoft.smartyoutubetv2.tv.presenter;

import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ListRowPresenter;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class CustomListRowPresenter extends ListRowPresenter {
    private int mPaddingStart;

    public CustomListRowPresenter() {
        super(ViewUtil.FOCUS_ZOOM_FACTOR, ViewUtil.FOCUS_DIMMER_ENABLED);
        setSelectEffectEnabled(ViewUtil.ROW_SELECT_EFFECT_ENABLED);
        setShadowEnabled(false); // MOD: no dark box behind the focused card (YouTube TV style)
    }

    // MOD: keep the focused card at the row start (YouTube TV style) instead of scrolling it to the middle
    @Override
    protected void initializeRowViewHolder(RowPresenter.ViewHolder holder) {
        super.initializeRowViewHolder(holder);
        HorizontalGridView grid = ((ListRowPresenter.ViewHolder) holder).getGridView();
        grid.setWindowAlignment(HorizontalGridView.WINDOW_ALIGN_NO_EDGE);
        grid.setWindowAlignmentOffset(mPaddingStart);
        grid.setWindowAlignmentOffsetPercent(HorizontalGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);
        grid.setItemAlignmentOffset(0);
        grid.setItemAlignmentOffsetPercent(HorizontalGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED);

        if (mPaddingStart > 0) {
            grid.setPaddingRelative(mPaddingStart, grid.getPaddingTop(), grid.getPaddingEnd(), grid.getPaddingBottom());
            if (holder.getHeaderViewHolder() != null) {
                android.view.View header = holder.getHeaderViewHolder().view;
                header.setPaddingRelative(mPaddingStart, header.getPaddingTop(), header.getPaddingEnd(), header.getPaddingBottom());
            }
        }
    }

    /**
     * Left margin for rows on screens without the sidebar (e.g. channel page)
     */
    public void setPaddingStart(int px) {
        mPaddingStart = px;
    }
}
