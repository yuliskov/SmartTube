package com.liskovsoft.smartyoutubetv2.tv.ui.modern.browse;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.leanback.widget.PresenterSelector;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernSpinner;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernStatePage;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernText;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernTheme;

/**
 * Page with a title, a list on the start side and a videos grid on the end side (subscriptions, channels).<br/>
 * The list is shown only when it's ready, the spinner covers the grid area only and the tiles fade in.
 */
abstract class ModernSideListView extends FrameLayout implements ModernSection, ModernSection.LoadingHost {
    /**
     * Short loads don't show the spinner at all.
     */
    private static final long SPINNER_DELAY_MS = 500;
    protected final ModernGridView mGrid;
    private final FrameLayout mColumn;
    private final View mDivider;
    private final ProgressBar mSpinner;
    private final int mGridShift;
    private final int mColumnShift;
    private final int mGridLeft;
    private final int mGridTop;
    private final int mColumnsWidth;
    private View mState;
    private boolean mColumnReady;
    private boolean mCollapsed;
    private final Runnable mShowSpinner = this::showSpinner;

    ModernSideListView(Context context, String title, VideoGroupPresenter presenter, PresenterSelector cardPresenter) {
        super(context);
        setClipChildren(false);
        Resources res = getResources();

        // Content starts below the top bar: compensate the browse content offset
        int offsetTop = -res.getDimensionPixelSize(R.dimen.modern_first_shelf_y);

        TextView titleView = new TextView(context);
        titleView.setText(ModernText.sentenceCase(title));
        titleView.setTextColor(ContextCompat.getColor(context, R.color.modern_text_primary));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimension(R.dimen.modern_subs_title_size));
        titleView.setTypeface(titleView.getTypeface(), Typeface.BOLD);
        titleView.setIncludeFontPadding(false);
        LayoutParams titleParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        titleParams.leftMargin = res.getDimensionPixelSize(R.dimen.modern_subs_title_left);
        titleParams.topMargin = offsetTop + res.getDimensionPixelSize(R.dimen.modern_subs_title_top);
        addView(titleView, titleParams);

        mColumn = new FrameLayout(context);
        mColumn.setAlpha(0f);
        LayoutParams columnParams = new LayoutParams(res.getDimensionPixelSize(R.dimen.modern_subs_column_width), LayoutParams.MATCH_PARENT);
        columnParams.leftMargin = res.getDimensionPixelSize(R.dimen.modern_subs_column_left);
        columnParams.topMargin = offsetTop + res.getDimensionPixelSize(R.dimen.modern_subs_column_top);
        addView(mColumn, columnParams);

        mDivider = new View(context);
        mDivider.setBackgroundColor(ContextCompat.getColor(context, R.color.modern_white_10));
        mDivider.setAlpha(0f);
        LayoutParams dividerParams = new LayoutParams(res.getDimensionPixelSize(R.dimen.modern_rail_divider), LayoutParams.MATCH_PARENT);
        dividerParams.leftMargin = res.getDimensionPixelSize(R.dimen.modern_subs_divider_left);
        dividerParams.topMargin = columnParams.topMargin;
        addView(mDivider, dividerParams);

        int gridLeft = res.getDimensionPixelSize(R.dimen.modern_subs_grid_left);
        int gridTop = offsetTop + res.getDimensionPixelSize(R.dimen.modern_subs_grid_top);

        mGrid = new ModernGridView(context, presenter, cardPresenter);
        mGrid.setContentLeft(gridLeft);
        mGrid.setSoftReplace(true);
        // While the grid has the focus the list slides out and the grid takes the content start: every column is fully visible
        mGridShift = res.getDimensionPixelSize(R.dimen.modern_content_left) - gridLeft;
        mColumnShift = -res.getDimensionPixelSize(R.dimen.modern_subs_divider_left);
        int gridWidth = gridLeft + mGrid.getColumnsWidth() + res.getDimensionPixelSize(R.dimen.modern_tile_gap);
        LayoutParams gridParams = new LayoutParams(gridWidth, LayoutParams.MATCH_PARENT);
        gridParams.topMargin = gridTop;
        addView(mGrid, gridParams);

        // Centered on the grid columns
        mSpinner = ModernSpinner.create(context);
        mSpinner.setVisibility(View.GONE);
        int spinnerSize = res.getDimensionPixelSize(R.dimen.modern_spinner_size);
        LayoutParams spinnerParams = new LayoutParams(spinnerSize, spinnerSize, Gravity.CENTER_VERTICAL);
        int columnsWidth = gridWidth - gridLeft - res.getDimensionPixelSize(R.dimen.modern_tile_gap);
        spinnerParams.leftMargin = gridLeft + (columnsWidth - spinnerSize) / 2;
        addView(mSpinner, spinnerParams);

        mGridLeft = gridLeft;
        mGridTop = gridTop;
        mColumnsWidth = columnsWidth;
    }

    /**
     * Empty or error state of the shown item (e.g. a channel without videos) in place of the videos: the list stays.
     */
    public void showState(View state) {
        boolean hadFocus = mGrid.hasFocus() || (mState != null && mState.hasFocus());
        clearState();
        mState = state;
        mState.setPadding(0, 0, 0, 0);
        LayoutParams params = new LayoutParams(mColumnsWidth, LayoutParams.MATCH_PARENT);
        params.leftMargin = mGridLeft;
        params.topMargin = mGridTop;
        addView(mState, params);
        mGrid.setVisibility(View.INVISIBLE);

        if (hadFocus && !ModernStatePage.focus(mState)) {
            focusColumn();
        }
    }

    public void clearState() {
        if (mState != null) {
            removeView(mState);
            mState = null;
            mGrid.setVisibility(View.VISIBLE);
        }
    }

    public boolean hasState() {
        return mState != null;
    }

    /**
     * Loads again the item shown in the grid ("Try again" of the state).
     */
    public abstract void reloadShown();

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        setCollapsed(child == mGrid);
    }

    /**
     * Grid focused: the list slides out to the start and the grid moves to the content start.
     */
    private void setCollapsed(boolean collapsed) {
        if (mCollapsed == collapsed) {
            return;
        }

        mCollapsed = collapsed;
        float columnAlpha = collapsed || !mColumnReady ? 0f : 1f;
        animateTo(mColumn, collapsed ? mColumnShift : 0, columnAlpha);
        animateTo(mDivider, collapsed ? mColumnShift : 0, columnAlpha);
        animateTo(mGrid, collapsed ? mGridShift : 0, 1f);
        animateTo(mSpinner, collapsed ? mGridShift : 0, 1f);
    }

    private static void animateTo(View view, float translationX, float alpha) {
        view.animate().translationX(translationX).alpha(alpha)
                .setDuration(ModernTheme.ANIM_SCROLL_MS).setInterpolator(ModernTheme.scrollInterpolator());
    }

    /**
     * The view of the start side list.
     */
    protected void setColumn(View column) {
        mColumn.addView(column, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    /**
     * The list is complete: show it.
     */
    protected void showColumn() {
        if (mColumnReady) {
            return;
        }

        mColumnReady = true;

        if (!mCollapsed) {
            mColumn.animate().alpha(1f).setDuration(ModernTheme.ANIM_FAST_MS).setInterpolator(ModernTheme.fadeInterpolator());
            mDivider.animate().alpha(1f).setDuration(ModernTheme.ANIM_FAST_MS).setInterpolator(ModernTheme.fadeInterpolator());
        }
    }

    /**
     * Left from the first grid column goes to the list, not to the rail.
     * @return true if the focus moved
     */
    public boolean onStartEdge() {
        return mGrid.hasFocus() && isColumnShown() && focusColumn();
    }

    protected abstract boolean focusColumn();

    public boolean isColumnShown() {
        return mColumnReady;
    }

    @Override
    public void showLoading(boolean show) {
        removeCallbacks(mShowSpinner);

        if (show) {
            postDelayed(mShowSpinner, SPINNER_DELAY_MS);
        } else {
            mSpinner.setVisibility(View.GONE);
            mGrid.onLoadFinished();
        }
    }

    private void showSpinner() {
        mSpinner.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(mShowSpinner);
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public void clear() {
        clearState();
        mGrid.clear();
    }

    @Override
    public boolean isEmpty() {
        return mGrid.isEmpty();
    }

    @Override
    public int getPosition() {
        return mGrid.getPosition();
    }

    @Override
    public void setPosition(int index) {
        mGrid.setPosition(index);
    }

    @Override
    public void selectItem(Video item) {
        mGrid.selectItem(item);
    }
}
