// CHECKSTYLE:OFF Generated code
/* This file is auto-generated from RowsSupportFragment.java.  DO NOT MODIFY. */

/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package androidx.leanback.app;

import android.animation.TimeAnimator;
import android.animation.TimeAnimator.TimeListener;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.R;
import androidx.leanback.widget.BaseOnItemViewClickedListener;
import androidx.leanback.widget.BaseOnItemViewSelectedListener;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.VerticalGridView;
import androidx.leanback.widget.ViewHolderTask;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

/**
 * An ordered set of rows of leanback widgets.
 * <p>
 * A RowsFragment renders the elements of its
 * {@link androidx.leanback.widget.ObjectAdapter} as a set
 * of rows in a vertical list. The Adapter's {@link PresenterSelector} must maintain subclasses
 * of {@link RowPresenter}.
 * </p>
 * @deprecated use {@link RowsSupportFragment}
 */
@Deprecated
public class RowsFragment extends BaseRowFragment implements
        BrowseFragment.MainFragmentRowsAdapterProvider,
        BrowseFragment.MainFragmentAdapterProvider {

    private MainFragmentAdapter mMainFragmentAdapter;
    private MainFragmentRowsAdapter mMainFragmentRowsAdapter;

    @Override
    public BrowseFragment.MainFragmentAdapter getMainFragmentAdapter() {
        if (mMainFragmentAdapter == null) {
            mMainFragmentAdapter = new MainFragmentAdapter(this);
        }
        return mMainFragmentAdapter;
    }

    @Override
    public BrowseFragment.MainFragmentRowsAdapter getMainFragmentRowsAdapter() {
        if (mMainFragmentRowsAdapter == null) {
            mMainFragmentRowsAdapter = new MainFragmentRowsAdapter(this);
        }
        return mMainFragmentRowsAdapter;
    }

    /**
     * Internal helper class that manages row select animation and apply a default
     * dim to each row.
     */
    final class RowViewHolderExtra implements TimeListener {
        final RowPresenter mRowPresenter;
        final Presenter.ViewHolder mRowViewHolder;

        final TimeAnimator mSelectAnimator = new TimeAnimator();

        int mSelectAnimatorDurationInUse;
        Interpolator mSelectAnimatorInterpolatorInUse;
        float mSelectLevelAnimStart;
        float mSelectLevelAnimDelta;

        RowViewHolderExtra(ItemBridgeAdapter.ViewHolder ibvh) {
            mRowPresenter = (RowPresenter) ibvh.getPresenter();
            mRowViewHolder = ibvh.getViewHolder();
            mSelectAnimator.setTimeListener(this);
        }

        @Override
        public void onTimeUpdate(TimeAnimator animation, long totalTime, long deltaTime) {
            if (mSelectAnimator.isRunning()) {
                updateSelect(totalTime, deltaTime);
            }
        }

        void updateSelect(long totalTime, long deltaTime) {
            float fraction;
            if (totalTime >= mSelectAnimatorDurationInUse) {
                fraction = 1;
                mSelectAnimator.end();
            } else {
                fraction = (float) (totalTime / (double) mSelectAnimatorDurationInUse);
            }
            if (mSelectAnimatorInterpolatorInUse != null) {
                fraction = mSelectAnimatorInterpolatorInUse.getInterpolation(fraction);
            }
            float level = mSelectLevelAnimStart + fraction * mSelectLevelAnimDelta;
            mRowPresenter.setSelectLevel(mRowViewHolder, level);
        }

        void animateSelect(boolean select, boolean immediate) {
            mSelectAnimator.end();
            final float end = select ? 1 : 0;
            if (immediate) {
                mRowPresenter.setSelectLevel(mRowViewHolder, end);
            } else if (mRowPresenter.getSelectLevel(mRowViewHolder) != end) {
                mSelectAnimatorDurationInUse = mSelectAnimatorDuration;
                mSelectAnimatorInterpolatorInUse = mSelectAnimatorInterpolator;
                mSelectLevelAnimStart = mRowPresenter.getSelectLevel(mRowViewHolder);
                mSelectLevelAnimDelta = end - mSelectLevelAnimStart;
                mSelectAnimator.start();
            }
        }

    }

    static final String TAG = "RowsFragment";
    static final boolean DEBUG = false;
    static final int ALIGN_TOP_NOT_SET = Integer.MIN_VALUE;

    ItemBridgeAdapter.ViewHolder mSelectedViewHolder;
    private int mSubPosition;
    boolean mExpand = true;
    boolean mViewsCreated;
    private int mAlignedTop = ALIGN_TOP_NOT_SET;
    boolean mAfterEntranceTransition = true;
    boolean mFreezeRows;

    BaseOnItemViewSelectedListener mOnItemViewSelectedListener;
    BaseOnItemViewClickedListener mOnItemViewClickedListener;

    // Select animation and interpolator are not intended to be
    // exposed at this moment. They might be synced with vertical scroll
    // animation later.
    int mSelectAnimatorDuration;
    Interpolator mSelectAnimatorInterpolator = new DecelerateInterpolator(2);

    private RecyclerView.RecycledViewPool mRecycledViewPool;
    private ArrayList<Presenter> mPresenterMapper;

    ItemBridgeAdapter.AdapterListener mExternalAdapterListener;

    @Override
    protected VerticalGridView findGridViewFromRoot(View view) {
        return (VerticalGridView) view.findViewById(R.id.container_list);
    }

    /**
     * Sets an item clicked listener on the fragment.
     * OnItemViewClickedListener will override {@link View.OnClickListener} that
     * item presenter sets during {@link Presenter#onCreateViewHolder(ViewGroup)}.
     * So in general, developer should choose one of the listeners but not both.
     */
    public void setOnItemViewClickedListener(BaseOnItemViewClickedListener listener) {
        mOnItemViewClickedListener = listener;
        if (mViewsCreated) {
            throw new IllegalStateException(
                    "Item clicked listener must be set before views are created");
        }
    }

    /**
     * Returns the item clicked listener.
     */
    public BaseOnItemViewClickedListener getOnItemViewClickedListener() {
        return mOnItemViewClickedListener;
    }

    /**
     * @deprecated use {@link BrowseFragment#enableRowScaling(boolean)} instead.
     *
     * @param enable true to enable row scaling
     */
    @Deprecated
    public void enableRowScaling(boolean enable) {
    }

    /**
     * Set the visibility of titles/hovercard of browse rows.
     */
    public void setExpand(boolean expand) {
        mExpand = expand;
        VerticalGridView listView = getVerticalGridView();
        if (listView != null) {
            final int count = listView.getChildCount();
            if (DEBUG) Log.v(TAG, "setExpand " + expand + " count " + count);
            for (int i = 0; i < count; i++) {
                View view = listView.getChildAt(i);
                ItemBridgeAdapter.ViewHolder vh =
                        (ItemBridgeAdapter.ViewHolder) listView.getChildViewHolder(view);
                setRowViewExpanded(vh, mExpand);
            }
        }
    }

    /**
     * Sets an item selection listener.
     */
    public void setOnItemViewSelectedListener(BaseOnItemViewSelectedListener listener) {
        mOnItemViewSelectedListener = listener;
        VerticalGridView listView = getVerticalGridView();
        if (listView != null) {
            final int count = listView.getChildCount();
            for (int i = 0; i < count; i++) {
                View view = listView.getChildAt(i);
                ItemBridgeAdapter.ViewHolder ibvh = (ItemBridgeAdapter.ViewHolder)
                        listView.getChildViewHolder(view);
                getRowViewHolder(ibvh).setOnItemViewSelectedListener(mOnItemViewSelectedListener);
            }
        }
    }

    /**
     * Returns an item selection listener.
     */
    public BaseOnItemViewSelectedListener getOnItemViewSelectedListener() {
        return mOnItemViewSelectedListener;
    }

    @Override
    void onRowSelected(RecyclerView parent, RecyclerView.ViewHolder viewHolder,
            int position, int subposition) {
        if (mSelectedViewHolder != viewHolder || mSubPosition != subposition) {
            if (DEBUG) Log.v(TAG, "new row selected position " + position + " subposition "
                    + subposition + " view " + viewHolder.itemView);
            mSubPosition = subposition;
            if (mSelectedViewHolder != null) {
                setRowViewSelected(mSelectedViewHolder, false, false);
            }
            mSelectedViewHolder = (ItemBridgeAdapter.ViewHolder) viewHolder;
            if (mSelectedViewHolder != null) {
                setRowViewSelected(mSelectedViewHolder, true, false);
            }
        }
        // When RowsFragment is embedded inside a page fragment, we want to show
        // the title view only when we're on the first row or there is no data.
        if (mMainFragmentAdapter != null) {
            mMainFragmentAdapter.getFragmentHost().showTitleView(position <= 0);
        }
    }

    /**
     * Get row ViewHolder at adapter position.  Returns null if the row object is not in adapter or
     * the row object has not been bound to a row view.
     *
     * @param position Position of row in adapter.
     * @return Row ViewHolder at a given adapter position.
     */
    public RowPresenter.ViewHolder getRowViewHolder(int position) {
        VerticalGridView verticalView = getVerticalGridView();
        if (verticalView == null) {
            return null;
        }
        return getRowViewHolder((ItemBridgeAdapter.ViewHolder)
                verticalView.findViewHolderForAdapterPosition(position));
    }

    @Override
    int getLayoutResourceId() {
        return R.layout.lb_rows_fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSelectAnimatorDuration = getResources().getInteger(
                R.integer.lb_browse_rows_anim_duration);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (DEBUG) Log.v(TAG, "onViewCreated");
        super.onViewCreated(view, savedInstanceState);
        // Align the top edge of child with id row_content.
        // Need set this for directly using RowsFragment.
        getVerticalGridView().setItemAlignmentViewId(R.id.row_content);
        getVerticalGridView().setSaveChildrenPolicy(VerticalGridView.SAVE_LIMITED_CHILD);

        setAlignment(mAlignedTop);

        mRecycledViewPool = null;
        mPresenterMapper = null;
        if (mMainFragmentAdapter != null) {
            mMainFragmentAdapter.getFragmentHost().notifyViewCreated(mMainFragmentAdapter);
        }

    }

    @Override
    public void onDestroyView() {
        mViewsCreated = false;
        super.onDestroyView();
    }

    void setExternalAdapterListener(ItemBridgeAdapter.AdapterListener listener) {
        mExternalAdapterListener = listener;
    }

    static void setRowViewExpanded(ItemBridgeAdapter.ViewHolder vh, boolean expanded) {
        ((RowPresenter) vh.getPresenter()).setRowViewExpanded(vh.getViewHolder(), expanded);
    }

    static void setRowViewSelected(ItemBridgeAdapter.ViewHolder vh, boolean selected,
            boolean immediate) {
        RowViewHolderExtra extra = (RowViewHolderExtra) vh.getExtraObject();
        extra.animateSelect(selected, immediate);
        ((RowPresenter) vh.getPresenter()).setRowViewSelected(vh.getViewHolder(), selected);
    }

    private final ItemBridgeAdapter.AdapterListener mBridgeAdapterListener =
            new ItemBridgeAdapter.AdapterListener() {
        @Override
        public void onAddPresenter(Presenter presenter, int type) {
            if (mExternalAdapterListener != null) {
                mExternalAdapterListener.onAddPresenter(presenter, type);
            }
        }

        @Override
        public void onCreate(ItemBridgeAdapter.ViewHolder vh) {
            VerticalGridView listView = getVerticalGridView();
            if (listView != null) {
                // set clip children false for slide animation
                listView.setClipChildren(false);
            }
            setupSharedViewPool(vh);
            mViewsCreated = true;
            vh.setExtraObject(new RowViewHolderExtra(vh));
            // selected state is initialized to false, then driven by grid view onChildSelected
            // events.  When there is rebind, grid view fires onChildSelected event properly.
            // So we don't need do anything special later in onBind or onAttachedToWindow.
            setRowViewSelected(vh, false, true);
            if (mExternalAdapterListener != null) {
                mExternalAdapterListener.onCreate(vh);
            }
            RowPresenter rowPresenter = (RowPresenter) vh.getPresenter();
            RowPresenter.ViewHolder rowVh = rowPresenter.getRowViewHolder(vh.getViewHolder());
            rowVh.setOnItemViewSelectedListener(mOnItemViewSelectedListener);
            rowVh.setOnItemViewClickedListener(mOnItemViewClickedListener);
        }

        @Override
        public void onAttachedToWindow(ItemBridgeAdapter.ViewHolder vh) {
            if (DEBUG) Log.v(TAG, "onAttachToWindow");
            // All views share the same mExpand value.  When we attach a view to grid view,
            // we should make sure it pick up the latest mExpand value we set early on other
            // attached views.  For no-structure-change update,  the view is rebound to new data,
            // but again it should use the unchanged mExpand value,  so we don't need do any
            // thing in onBind.
            setRowViewExpanded(vh, mExpand);
            RowPresenter rowPresenter = (RowPresenter) vh.getPresenter();
            RowPresenter.ViewHolder rowVh = rowPresenter.getRowViewHolder(vh.getViewHolder());
            rowPresenter.setEntranceTransitionState(rowVh, mAfterEntranceTransition);

            // freeze the rows attached after RowsFragment#freezeRows() is called
            rowPresenter.freeze(rowVh, mFreezeRows);

            if (mExternalAdapterListener != null) {
                mExternalAdapterListener.onAttachedToWindow(vh);
            }
        }

        @Override
        public void onDetachedFromWindow(ItemBridgeAdapter.ViewHolder vh) {
            if (mSelectedViewHolder == vh) {
                setRowViewSelected(mSelectedViewHolder, false, true);
                mSelectedViewHolder = null;
            }
            if (mExternalAdapterListener != null) {
                mExternalAdapterListener.onDetachedFromWindow(vh);
            }
        }

        @Override
        public void onBind(ItemBridgeAdapter.ViewHolder vh) {
            if (mExternalAdapterListener != null) {
                mExternalAdapterListener.onBind(vh);
            }
        }

        @Override
        public void onUnbind(ItemBridgeAdapter.ViewHolder vh) {
            setRowViewSelected(vh, false, true);
            if (mExternalAdapterListener != null) {
                mExternalAdapterListener.onUnbind(vh);
            }
        }
    };

    void setupSharedViewPool(ItemBridgeAdapter.ViewHolder bridgeVh) {
        RowPresenter rowPresenter = (RowPresenter) bridgeVh.getPresenter();
        RowPresenter.ViewHolder rowVh = rowPresenter.getRowViewHolder(bridgeVh.getViewHolder());

        if (rowVh instanceof ListRowPresenter.ViewHolder) {
            HorizontalGridView view = ((ListRowPresenter.ViewHolder) rowVh).getGridView();
            // Recycled view pool is shared between all list rows
            if (mRecycledViewPool == null) {
                mRecycledViewPool = view.getRecycledViewPool();
            } else {
                view.setRecycledViewPool(mRecycledViewPool);
            }

            ItemBridgeAdapter bridgeAdapter =
                    ((ListRowPresenter.ViewHolder) rowVh).getBridgeAdapter();
            if (mPresenterMapper == null) {
                mPresenterMapper = bridgeAdapter.getPresenterMapper();
            } else {
                bridgeAdapter.setPresenterMapper(mPresenterMapper);
            }
        }
    }

    @Override
    void updateAdapter() {
        super.updateAdapter();
        mSelectedViewHolder = null;
        mViewsCreated = false;

        ItemBridgeAdapter adapter = getBridgeAdapter();
        if (adapter != null) {
            adapter.setAdapterListener(mBridgeAdapterListener);
        }
    }

    @Override
    public boolean onTransitionPrepare() {
        boolean prepared = super.onTransitionPrepare();
        if (prepared) {
            freezeRows(true);
        }
        return prepared;
    }

    @Override
    public void onTransitionEnd() {
        super.onTransitionEnd();
        freezeRows(false);
    }

    private void freezeRows(boolean freeze) {
        mFreezeRows = freeze;
        VerticalGridView verticalView = getVerticalGridView();
        if (verticalView != null) {
            final int count = verticalView.getChildCount();
            for (int i = 0; i < count; i++) {
                ItemBridgeAdapter.ViewHolder ibvh = (ItemBridgeAdapter.ViewHolder)
                        verticalView.getChildViewHolder(verticalView.getChildAt(i));
                RowPresenter rowPresenter = (RowPresenter) ibvh.getPresenter();
                RowPresenter.ViewHolder vh = rowPresenter.getRowViewHolder(ibvh.getViewHolder());
                rowPresenter.freeze(vh, freeze);
            }
        }
    }

    /**
     * For rows that willing to participate entrance transition,  this function
     * hide views if afterTransition is true,  show views if afterTransition is false.
     */
    public void setEntranceTransitionState(boolean afterTransition) {
        mAfterEntranceTransition = afterTransition;
        VerticalGridView verticalView = getVerticalGridView();
        if (verticalView != null) {
            final int count = verticalView.getChildCount();
            for (int i = 0; i < count; i++) {
                ItemBridgeAdapter.ViewHolder ibvh = (ItemBridgeAdapter.ViewHolder)
                        verticalView.getChildViewHolder(verticalView.getChildAt(i));
                RowPresenter rowPresenter = (RowPresenter) ibvh.getPresenter();
                RowPresenter.ViewHolder vh = rowPresenter.getRowViewHolder(ibvh.getViewHolder());
                rowPresenter.setEntranceTransitionState(vh, mAfterEntranceTransition);
            }
        }
    }

    /**
     * Selects a Row and perform an optional task on the Row. For example
     * <code>setSelectedPosition(10, true, new ListRowPresenterSelectItemViewHolderTask(5))</code>
     * Scroll to 11th row and selects 6th item on that row.  The method will be ignored if
     * RowsFragment has not been created (i.e. before {@link #onCreateView(LayoutInflater,
     * ViewGroup, Bundle)}).
     *
     * @param rowPosition Which row to select.
     * @param smooth True to scroll to the row, false for no animation.
     * @param rowHolderTask Task to perform on the Row.
     */
    public void setSelectedPosition(int rowPosition, boolean smooth,
            final Presenter.ViewHolderTask rowHolderTask) {
        VerticalGridView verticalView = getVerticalGridView();
        if (verticalView == null) {
            return;
        }
        ViewHolderTask task = null;
        if (rowHolderTask != null) {
            // This task will execute once the scroll completes. Once the scrolling finishes,
            // we will get a success callback to update selected row position. Since the
            // update to selected row position happens in a post, we want to ensure that this
            // gets called after that.
            task = new ViewHolderTask() {
                @Override
                public void run(final RecyclerView.ViewHolder rvh) {
                    rvh.itemView.post(new Runnable() {
                        @Override
                        public void run() {
                            rowHolderTask.run(
                                    getRowViewHolder((ItemBridgeAdapter.ViewHolder) rvh));
                        }
                    });
                }
            };
        }

        if (smooth) {
            verticalView.setSelectedPositionSmooth(rowPosition, task);
        } else {
            verticalView.setSelectedPosition(rowPosition, task);
        }
    }

    static RowPresenter.ViewHolder getRowViewHolder(ItemBridgeAdapter.ViewHolder ibvh) {
        if (ibvh == null) {
            return null;
        }
        RowPresenter rowPresenter = (RowPresenter) ibvh.getPresenter();
        return rowPresenter.getRowViewHolder(ibvh.getViewHolder());
    }

    public boolean isScrolling() {
        if (getVerticalGridView() == null) {
            return false;
        }
        return getVerticalGridView().getScrollState() != HorizontalGridView.SCROLL_STATE_IDLE;
    }

    @Override
    public void setAlignment(int windowAlignOffsetFromTop) {
        if (windowAlignOffsetFromTop == ALIGN_TOP_NOT_SET) {
            return;
        }
        mAlignedTop = windowAlignOffsetFromTop;
        final VerticalGridView gridView = getVerticalGridView();

        if (gridView != null) {
            gridView.setItemAlignmentOffset(0);
            gridView.setItemAlignmentOffsetPercent(
                    VerticalGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED);
            gridView.setItemAlignmentOffsetWithPadding(true);
            gridView.setWindowAlignmentOffset(mAlignedTop);
            // align to a fixed position from top
            gridView.setWindowAlignmentOffsetPercent(
                    VerticalGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);
            gridView.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_NO_EDGE);
        }
    }

    /**
     * Find row ViewHolder by position in adapter.
     * @param position Position of row.
     * @return ViewHolder of Row.
     */
    public RowPresenter.ViewHolder findRowViewHolderByPosition(int position) {
        if (mVerticalGridView == null) {
            return null;
        }
        return getRowViewHolder((ItemBridgeAdapter.ViewHolder) mVerticalGridView
                .findViewHolderForAdapterPosition(position));
    }

    public static class MainFragmentAdapter extends BrowseFragment.MainFragmentAdapter<RowsFragment> {

        public MainFragmentAdapter(RowsFragment fragment) {
            super(fragment);
            setScalingEnabled(true);
        }

        @Override
        public boolean isScrolling() {
            return getFragment().isScrolling();
        }

        @Override
        public void setExpand(boolean expand) {
            getFragment().setExpand(expand);
        }

        @Override
        public void setEntranceTransitionState(boolean state) {
            getFragment().setEntranceTransitionState(state);
        }

        @Override
        public void setAlignment(int windowAlignOffsetFromTop) {
            getFragment().setAlignment(windowAlignOffsetFromTop);
        }

        @Override
        public boolean onTransitionPrepare() {
            return getFragment().onTransitionPrepare();
        }

        @Override
        public void onTransitionStart() {
            getFragment().onTransitionStart();
        }

        @Override
        public void onTransitionEnd() {
            getFragment().onTransitionEnd();
        }

    }

    /**
     * The adapter that RowsFragment implements
     * BrowseFragment.MainFragmentRowsAdapter.
     * @see #getMainFragmentRowsAdapter().
     * @deprecated use {@link RowsSupportFragment}
     */
    @Deprecated
    public static class MainFragmentRowsAdapter
            extends BrowseFragment.MainFragmentRowsAdapter<RowsFragment> {

        public MainFragmentRowsAdapter(RowsFragment fragment) {
            super(fragment);
        }

        @Override
        public void setAdapter(ObjectAdapter adapter) {
            getFragment().setAdapter(adapter);
        }

        /**
         * Sets an item clicked listener on the fragment.
         */
        @Override
        public void setOnItemViewClickedListener(OnItemViewClickedListener listener) {
            getFragment().setOnItemViewClickedListener(listener);
        }

        @Override
        public void setOnItemViewSelectedListener(OnItemViewSelectedListener listener) {
            getFragment().setOnItemViewSelectedListener(listener);
        }

        @Override
        public void setSelectedPosition(int rowPosition,
                                        boolean smooth,
                                        final Presenter.ViewHolderTask rowHolderTask) {
            getFragment().setSelectedPosition(rowPosition, smooth, rowHolderTask);
        }

        @Override
        public void setSelectedPosition(int rowPosition, boolean smooth) {
            getFragment().setSelectedPosition(rowPosition, smooth);
        }

        @Override
        public int getSelectedPosition() {
            return getFragment().getSelectedPosition();
        }

        @Override
        public RowPresenter.ViewHolder findRowViewHolderByPosition(int position) {
            return getFragment().findRowViewHolderByPosition(position);
        }
    }
}
