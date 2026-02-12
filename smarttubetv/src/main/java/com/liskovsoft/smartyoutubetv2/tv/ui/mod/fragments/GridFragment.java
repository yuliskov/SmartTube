package com.liskovsoft.smartyoutubetv2.tv.ui.mod.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.transition.TransitionHelper;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.OnChildLaidOutListener;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.VerticalGridPresenter;
import androidx.leanback.widget.VerticalGridView;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * NOTE: All code was new. It's copied from:<br/>
 * https://github.com/googlearchive/leanback-showcase/blob/master/app/src/main/java/android/support/v17/leanback/supportleanbackshowcase/app/page/PageAndListRowFragment.java<br/>
 * Description: A fragment for rendering items in a vertical grids.
 */
public class GridFragment extends Fragment implements BrowseSupportFragment.MainFragmentAdapterProvider {
    private static final String TAG = "VerticalGridFragment";
    private static boolean DEBUG = false;

    private ObjectAdapter mAdapter;
    private VerticalGridPresenter mGridPresenter;
    private VerticalGridPresenter.ViewHolder mGridViewHolder;
    private OnItemViewSelectedListener mOnItemViewSelectedListener;
    private OnItemViewClickedListener mOnItemViewClickedListener;
    private Object mSceneAfterEntranceTransition;
    private int mSelectedPosition = -1;
    private final BrowseSupportFragment.MainFragmentAdapter<Fragment> mMainFragmentAdapter =
            new BrowseSupportFragment.MainFragmentAdapter<Fragment>(this) {
                @Override
                public void setEntranceTransitionState(boolean state) {
                    GridFragment.this.setEntranceTransitionState(state);
                }
            };
    /**
     * Sets the grid presenter.
     */
    public void setGridPresenter(VerticalGridPresenter gridPresenter) {
        if (gridPresenter == null) {
            throw new IllegalArgumentException("Grid presenter may not be null");
        }
        mGridPresenter = gridPresenter;
        mGridPresenter.setOnItemViewSelectedListener(mViewSelectedListener);
        if (mOnItemViewClickedListener != null) {
            mGridPresenter.setOnItemViewClickedListener(mOnItemViewClickedListener);
        }
    }

    /**
     * Returns the grid presenter.
     */
    public VerticalGridPresenter getGridPresenter() {
        return mGridPresenter;
    }

    /**
     * Returns R.id.browse_grid
     */
    public VerticalGridView getBrowseGrid() {
        return mGridViewHolder != null ? mGridViewHolder.getGridView() : null;
    }

    /**
     * Sets the object adapter for the fragment.
     */
    public void setAdapter(ObjectAdapter adapter) {
        mAdapter = adapter;
        updateAdapter();
    }

    /**
     * Returns the object adapter.
     */
    public ObjectAdapter getAdapter() {
        return mAdapter;
    }

    final private OnItemViewSelectedListener mViewSelectedListener =
            new OnItemViewSelectedListener() {
                @Override
                public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                           RowPresenter.ViewHolder rowViewHolder, Row row) {
                    if (mGridViewHolder == null) {
                        return;
                    }
                    int position = mGridViewHolder.getGridView().getSelectedPosition();
                    if (DEBUG) Log.v(TAG, "grid selected position " + position);
                    gridOnItemSelected(position);
                    if (mOnItemViewSelectedListener != null) {
                        mOnItemViewSelectedListener.onItemSelected(itemViewHolder, item,
                                rowViewHolder, row);
                    }
                }
            };

    final private OnChildLaidOutListener mChildLaidOutListener =
            new OnChildLaidOutListener() {
                @Override
                public void onChildLaidOut(ViewGroup parent, View view, int position, long id) {
                    if (position == 0) {
                        showOrHideTitle();
                    }
                }
            };

    /**
     * Sets an item selection listener.
     */
    public void setOnItemViewSelectedListener(OnItemViewSelectedListener listener) {
        mOnItemViewSelectedListener = listener;

        // Don't do that. We have hook here for show/hide title view.
        //if (mGridPresenter != null) {
        //    mGridPresenter.setOnItemViewSelectedListener(mOnItemViewSelectedListener);
        //}
    }

    private void gridOnItemSelected(int position) {
        if (position != mSelectedPosition) {
            mSelectedPosition = position;
            showOrHideTitle();
        }
    }

    protected void showOrHideTitle() {
        if (mGridViewHolder.getGridView().findViewHolderForAdapterPosition(mSelectedPosition)
                == null || mMainFragmentAdapter.getFragmentHost() == null) {
            return;
        }
        if (!mGridViewHolder.getGridView().hasPreviousViewInSameRow(mSelectedPosition)) {
            mMainFragmentAdapter.getFragmentHost().showTitleView(true);
        } else {
            mMainFragmentAdapter.getFragmentHost().showTitleView(false);
        }
    }

    /**
     * Sets an item clicked listener.
     */
    public void setOnItemViewClickedListener(OnItemViewClickedListener listener) {
        mOnItemViewClickedListener = listener;
        if (mGridPresenter != null) {
            mGridPresenter.setOnItemViewClickedListener(mOnItemViewClickedListener);
        }
    }

    /**
     * Returns the item clicked listener.
     */
    public OnItemViewClickedListener getOnItemViewClickedListener() {
        return mOnItemViewClickedListener;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_grid, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewGroup gridDock = (ViewGroup) view.findViewById(R.id.browse_grid_dock);
        mGridViewHolder = mGridPresenter.onCreateViewHolder(gridDock);
        gridDock.addView(mGridViewHolder.view);
        mGridViewHolder.getGridView().setOnChildLaidOutListener(mChildLaidOutListener);

        mSceneAfterEntranceTransition = TransitionHelper.createScene(gridDock, new Runnable() {
            @Override
            public void run() {
                setEntranceTransitionState(true);
            }
        });

        if (getMainFragmentAdapter().getFragmentHost() != null) {
            getMainFragmentAdapter().getFragmentHost().notifyViewCreated(mMainFragmentAdapter);
        }

        updateAdapter();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mGridViewHolder = null;
    }

    @Override
    public BrowseSupportFragment.MainFragmentAdapter<Fragment> getMainFragmentAdapter() {
        return mMainFragmentAdapter;
    }

    /**
     * Sets the selected item position.
     */
    public void setSelectedPosition(int position) {
        mSelectedPosition = position;
        if (mGridViewHolder != null && mGridViewHolder.getGridView().getAdapter() != null) {
            mGridViewHolder.getGridView().setSelectedPosition(position);
        }
    }

    public int getSelectedPosition() {
        return mSelectedPosition;
    }

    private void updateAdapter() {
        if (mGridViewHolder != null) {
            mGridPresenter.onBindViewHolder(mGridViewHolder, mAdapter);
            if (mSelectedPosition != -1) {
                mGridViewHolder.getGridView().setSelectedPosition(mSelectedPosition);
            }
        }
    }

    void setEntranceTransitionState(boolean afterTransition) {
        mGridPresenter.setEntranceTransitionState(mGridViewHolder, afterTransition);
    }
}
