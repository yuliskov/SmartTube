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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.leanback.widget.PresenterSelector;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.RecyclerView;

/**
 * An internal base class for a fragment containing a list of rows.
 */
abstract class BaseRowSupportFragment extends Fragment {
    private static final String CURRENT_SELECTED_POSITION = "currentSelectedPosition";
    private ObjectAdapter mAdapter;
    VerticalGridView mVerticalGridView;
    private PresenterSelector mPresenterSelector;
    final ItemBridgeAdapter mBridgeAdapter = new ItemBridgeAdapter();
    int mSelectedPosition = -1;
    private boolean mPendingTransitionPrepare;
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    LateSelectionObserver mLateSelectionObserver = new LateSelectionObserver();

    abstract int getLayoutResourceId();

    private final OnChildViewHolderSelectedListener mRowSelectedListener =
            new OnChildViewHolderSelectedListener() {
                @Override
                public void onChildViewHolderSelected(RecyclerView parent,
                        RecyclerView.ViewHolder view, int position, int subposition) {
                    if (!mLateSelectionObserver.mIsLateSelection) {
                        mSelectedPosition = position;
                        onRowSelected(parent, view, position, subposition);
                    }
                }
            };

    void onRowSelected(RecyclerView parent, RecyclerView.ViewHolder view,
            int position, int subposition) {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(getLayoutResourceId(), container, false);
        mVerticalGridView = findGridViewFromRoot(view);
        if (mPendingTransitionPrepare) {
            mPendingTransitionPrepare = false;
            onTransitionPrepare();
        }
        return view;
    }

    VerticalGridView findGridViewFromRoot(View view) {
        return (VerticalGridView) view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            mSelectedPosition = savedInstanceState.getInt(CURRENT_SELECTED_POSITION, -1);
        }
        setAdapterAndSelection();
        mVerticalGridView.setOnChildViewHolderSelectedListener(mRowSelectedListener);
    }

    /**
     * This class waits for the adapter to be updated before setting the selected
     * row.
     */
    private class LateSelectionObserver extends RecyclerView.AdapterDataObserver {
        boolean mIsLateSelection = false;

        LateSelectionObserver() {
        }

        @Override
        public void onChanged() {
            performLateSelection();
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            performLateSelection();
        }

        void startLateSelection() {
            mIsLateSelection = true;
            mBridgeAdapter.registerAdapterDataObserver(this);
        }

        void performLateSelection() {
            clear();
            if (mVerticalGridView != null) {
                mVerticalGridView.setSelectedPosition(mSelectedPosition);
            }
        }

        void clear() {
            if (mIsLateSelection) {
                mIsLateSelection = false;
                mBridgeAdapter.unregisterAdapterDataObserver(this);
            }
        }
    }

    void setAdapterAndSelection() {
        if (mAdapter == null) {
            // delay until ItemBridgeAdapter has wrappedAdapter. Once we assign ItemBridgeAdapter
            // to RecyclerView, it will not be allowed to change "hasStableId" to true.
            return;
        }
        if (mVerticalGridView.getAdapter() != mBridgeAdapter) {
            // avoid extra layout if ItemBridgeAdapter was already set.
            mVerticalGridView.setAdapter(mBridgeAdapter);
        }
        // We don't set the selected position unless we've data in the adapter.
        boolean lateSelection = mBridgeAdapter.getItemCount() == 0 && mSelectedPosition >= 0;
        if (lateSelection) {
            mLateSelectionObserver.startLateSelection();
        } else if (mSelectedPosition >= 0) {
            mVerticalGridView.setSelectedPosition(mSelectedPosition);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mLateSelectionObserver.clear();
        mVerticalGridView = null;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(CURRENT_SELECTED_POSITION, mSelectedPosition);
    }

    /**
     * Set the presenter selector used to create and bind views.
     */
    public final void setPresenterSelector(PresenterSelector presenterSelector) {
        if (mPresenterSelector != presenterSelector) {
            mPresenterSelector = presenterSelector;
            updateAdapter();
        }
    }

    /**
     * Get the presenter selector used to create and bind views.
     */
    public final PresenterSelector getPresenterSelector() {
        return mPresenterSelector;
    }

    /**
     * Sets the adapter that represents a list of rows.
     * @param rowsAdapter Adapter that represents list of rows.
     */
    public final void setAdapter(ObjectAdapter rowsAdapter) {
        if (mAdapter != rowsAdapter) {
            mAdapter = rowsAdapter;
            updateAdapter();
        }
    }

    /**
     * Returns the Adapter that represents list of rows.
     * @return Adapter that represents list of rows.
     */
    public final ObjectAdapter getAdapter() {
        return mAdapter;
    }

    /**
     * Returns the RecyclerView.Adapter that wraps {@link #getAdapter()}.
     * @return The RecyclerView.Adapter that wraps {@link #getAdapter()}.
     */
    public final ItemBridgeAdapter getBridgeAdapter() {
        return mBridgeAdapter;
    }

    /**
     * Sets the selected row position with smooth animation.
     */
    public void setSelectedPosition(int position) {
        setSelectedPosition(position, true);
    }

    /**
     * Gets position of currently selected row.
     * @return Position of currently selected row.
     */
    public int getSelectedPosition() {
        return mSelectedPosition;
    }

    /**
     * Sets the selected row position.
     */
    public void setSelectedPosition(int position, boolean smooth) {
        if (mSelectedPosition == position) {
            return;
        }
        mSelectedPosition = position;
        if (mVerticalGridView != null) {
            if (mLateSelectionObserver.mIsLateSelection) {
                return;
            }
            if (smooth) {
                mVerticalGridView.setSelectedPositionSmooth(position);
            } else {
                mVerticalGridView.setSelectedPosition(position);
            }
        }
    }

    public final VerticalGridView getVerticalGridView() {
        return mVerticalGridView;
    }

    void updateAdapter() {
        mBridgeAdapter.setAdapter(mAdapter);
        mBridgeAdapter.setPresenter(mPresenterSelector);

        if (mVerticalGridView != null) {
            setAdapterAndSelection();
        }
    }

    Object getItem(Row row, int position) {
        if (row instanceof ListRow) {
            return ((ListRow) row).getAdapter().get(position);
        } else {
            return null;
        }
    }

    public boolean onTransitionPrepare() {
        if (mVerticalGridView != null) {
            mVerticalGridView.setAnimateChildLayout(false);
            mVerticalGridView.setScrollEnabled(false);
            return true;
        }
        mPendingTransitionPrepare = true;
        return false;
    }

    public void onTransitionStart() {
        if (mVerticalGridView != null) {
            mVerticalGridView.setPruneChild(false);
            mVerticalGridView.setLayoutFrozen(true);
            mVerticalGridView.setFocusSearchDisabled(true);
        }
    }

    public void onTransitionEnd() {
        // be careful that fragment might be destroyed before header transition ends.
        if (mVerticalGridView != null) {
            mVerticalGridView.setLayoutFrozen(false);
            mVerticalGridView.setAnimateChildLayout(true);
            mVerticalGridView.setPruneChild(true);
            mVerticalGridView.setFocusSearchDisabled(false);
            mVerticalGridView.setScrollEnabled(true);
        }
    }

    public void setAlignment(int windowAlignOffsetTop) {
        if (mVerticalGridView != null) {
            // align the top edge of item
            mVerticalGridView.setItemAlignmentOffset(0);
            mVerticalGridView.setItemAlignmentOffsetPercent(
                    VerticalGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED);

            // align to a fixed position from top
            mVerticalGridView.setWindowAlignmentOffset(windowAlignOffsetTop);
            mVerticalGridView.setWindowAlignmentOffsetPercent(
                    VerticalGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);
            mVerticalGridView.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_NO_EDGE);
        }
    }
}
