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
 * Managing multiple grids at a time.<br/>
 * Based on code from the {@link GridFragment}<br/>
 * TODO: somehow fix same id (R.id.browse_grid) for both grids in {@link VerticalGridPresenter}
 */
public class MultiGridFragment extends Fragment implements BrowseSupportFragment.MainFragmentAdapterProvider {
    private static final String TAG = "VerticalGridFragment";
    private static boolean DEBUG = false;

    private ObjectAdapter mAdapter1;
    private ObjectAdapter mAdapter2;
    private VerticalGridPresenter mGridPresenter1;
    private VerticalGridPresenter mGridPresenter2;
    private VerticalGridPresenter.ViewHolder mGridViewHolder1;
    private VerticalGridPresenter.ViewHolder mGridViewHolder2;
    private OnItemViewSelectedListener mOnItemViewSelectedListener1;
    private OnItemViewSelectedListener mOnItemViewSelectedListener2;
    private OnItemViewClickedListener mOnItemViewClickedListener1;
    private OnItemViewClickedListener mOnItemViewClickedListener2;
    private Object mSceneAfterEntranceTransition;
    private int mSelectedPosition1 = -1;
    private int mSelectedPosition2 = -1;
    private final BrowseSupportFragment.MainFragmentAdapter<Fragment> mMainFragmentAdapter =
            new BrowseSupportFragment.MainFragmentAdapter<Fragment>(this) {
                @Override
                public void setEntranceTransitionState(boolean state) {
                    MultiGridFragment.this.setEntranceTransitionState(state);
                }
            };

    /**
     * Sets the grid1 presenter.
     */
    public void setGridPresenter1(VerticalGridPresenter gridPresenter1) {
        if (gridPresenter1 == null) {
            throw new IllegalArgumentException("Grid presenter may not be null");
        }
        mGridPresenter1 = gridPresenter1;
        mGridPresenter1.setOnItemViewSelectedListener(mViewSelectedListener1);
        if (mOnItemViewClickedListener1 != null) {
            mGridPresenter1.setOnItemViewClickedListener(mOnItemViewClickedListener1);
        }
    }

    /**
     * Sets the grid1 presenter.
     */
    public void setGridPresenter2(VerticalGridPresenter gridPresenter2) {
        if (gridPresenter2 == null) {
            throw new IllegalArgumentException("Grid presenter may not be null");
        }
        mGridPresenter2 = gridPresenter2;
        mGridPresenter2.setOnItemViewSelectedListener(mViewSelectedListener2);
        if (mOnItemViewClickedListener2 != null) {
            mGridPresenter2.setOnItemViewClickedListener(mOnItemViewClickedListener2);
        }
    }

    /**
     * Returns the grid1 presenter.
     */
    public VerticalGridPresenter getGridPresenter1() {
        return mGridPresenter1;
    }

    /**
     * Returns the grid2 presenter.
     */
    public VerticalGridPresenter getGridPresenter2() {
        return mGridPresenter2;
    }

    /**
     * TODO: Returns R.id.browse_grid
     */
    public VerticalGridView getBrowseGrid1() {
        return mGridViewHolder1.getGridView();
    }

    /**
     * TODO: Returns R.id.browse_grid
     */
    public VerticalGridView getBrowseGrid2() {
        return mGridViewHolder2.getGridView();
    }

    /**
     * Sets the object adapter for the fragment.
     */
    public void setAdapter1(ObjectAdapter adapter) {
        mAdapter1 = adapter;
        updateAdapter1();
    }

    /**
     * Returns the object adapter.
     */
    public ObjectAdapter getAdapter1() {
        return mAdapter1;
    }

    /**
     * Sets the object adapter for the fragment.
     */
    public void setAdapter2(ObjectAdapter adapter) {
        mAdapter2 = adapter;
        updateAdapter2();
    }

    /**
     * Returns the object adapter.
     */
    public ObjectAdapter getAdapter2() {
        return mAdapter2;
    }

    final private OnItemViewSelectedListener mViewSelectedListener1 =
            new OnItemViewSelectedListener() {
                @Override
                public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                           RowPresenter.ViewHolder rowViewHolder, Row row) {
                    if (mGridViewHolder1 == null) {
                        return;
                    }
                    int position = mGridViewHolder1.getGridView().getSelectedPosition();
                    if (DEBUG) Log.v(TAG, "grid selected position " + position);
                    gridOnItemSelected(position);
                    if (mOnItemViewSelectedListener1 != null) {
                        mOnItemViewSelectedListener1.onItemSelected(itemViewHolder, item,
                                rowViewHolder, row);
                    }
                }
            };

    final private OnItemViewSelectedListener mViewSelectedListener2 =
            new OnItemViewSelectedListener() {
                @Override
                public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                           RowPresenter.ViewHolder rowViewHolder, Row row) {
                    if (mGridViewHolder2 == null) {
                        return;
                    }
                    int position = mGridViewHolder2.getGridView().getSelectedPosition();
                    if (DEBUG) Log.v(TAG, "grid selected position " + position);
                    gridOnItemSelected(position);
                    if (mOnItemViewSelectedListener2 != null) {
                        mOnItemViewSelectedListener2.onItemSelected(itemViewHolder, item,
                                rowViewHolder, row);
                    }
                }
            };

    final private OnChildLaidOutListener mChildLaidOutListener1 =
            new OnChildLaidOutListener() {
                @Override
                public void onChildLaidOut(ViewGroup parent, View view, int position, long id) {
                    if (position == 0) {
                        // Don't control title visibility on first column?
                        showOrHideTitle1();
                    }
                }
            };

    final private OnChildLaidOutListener mChildLaidOutListener2 =
            new OnChildLaidOutListener() {
                @Override
                public void onChildLaidOut(ViewGroup parent, View view, int position, long id) {
                    if (position == 0) {
                        showOrHideTitle2();
                    }
                }
            };

    /**
     * Sets an item selection listener for the grid1.
     */
    public void setOnItemViewSelectedListener1(OnItemViewSelectedListener listener) {
        mOnItemViewSelectedListener1 = listener;
    }

    /**
     * Sets an item selection listener for the grid2.
     */
    public void setOnItemViewSelectedListener2(OnItemViewSelectedListener listener) {
        mOnItemViewSelectedListener2 = listener;
    }

    private void gridOnItemSelected(int position) {
        if (position != mSelectedPosition1) {
            mSelectedPosition1 = position;
            showOrHideTitle1();
        }
    }

    private void showOrHideTitle1() {
        if (mGridViewHolder1.getGridView().findViewHolderForAdapterPosition(mSelectedPosition1)
                == null || mMainFragmentAdapter.getFragmentHost() == null) {
            return;
        }
        if (!mGridViewHolder1.getGridView().hasPreviousViewInSameRow(mSelectedPosition1)) {
            mMainFragmentAdapter.getFragmentHost().showTitleView(true);
        } else {
            mMainFragmentAdapter.getFragmentHost().showTitleView(false);
        }
    }

    private void showOrHideTitle2() {
        if (mGridViewHolder2.getGridView().findViewHolderForAdapterPosition(mSelectedPosition2)
                == null || mMainFragmentAdapter.getFragmentHost() == null) {
            return;
        }
        if (!mGridViewHolder2.getGridView().hasPreviousViewInSameRow(mSelectedPosition2)) {
            mMainFragmentAdapter.getFragmentHost().showTitleView(true);
        } else {
            mMainFragmentAdapter.getFragmentHost().showTitleView(false);
        }
    }

    /**
     * Sets an item clicked listener.
     */
    public void setOnItemViewClickedListener1(OnItemViewClickedListener listener) {
        mOnItemViewClickedListener1 = listener;
        if (mGridPresenter1 != null) {
            mGridPresenter1.setOnItemViewClickedListener(mOnItemViewClickedListener1);
        }
    }

    /**
     * Sets an item clicked listener.
     */
    public void setOnItemViewClickedListener2(OnItemViewClickedListener listener) {
        mOnItemViewClickedListener2 = listener;
        if (mGridPresenter1 != null) {
            mGridPresenter1.setOnItemViewClickedListener(mOnItemViewClickedListener2);
        }
    }

    /**
     * Returns the item clicked listener.
     */
    public OnItemViewClickedListener getOnItemViewClickedListener1() {
        return mOnItemViewClickedListener1;
    }

    /**
     * Returns the item clicked listener.
     */
    public OnItemViewClickedListener getOnItemViewClickedListener2() {
        return mOnItemViewClickedListener2;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_multi_grid, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewGroup gridDock = (ViewGroup) view.findViewById(R.id.browse_grid_dock);

        mGridViewHolder1 = mGridPresenter1.onCreateViewHolder(gridDock);
        gridDock.addView(mGridViewHolder1.view);
        mGridViewHolder1.getGridView().setOnChildLaidOutListener(mChildLaidOutListener1);

        mGridViewHolder2 = mGridPresenter2.onCreateViewHolder(gridDock);
        gridDock.addView(mGridViewHolder2.view);
        mGridViewHolder2.getGridView().setOnChildLaidOutListener(mChildLaidOutListener2);

        mSceneAfterEntranceTransition = TransitionHelper.createScene(gridDock, new Runnable() {
            @Override
            public void run() {
                setEntranceTransitionState(true);
            }
        });

        if (getMainFragmentAdapter().getFragmentHost() != null) {
            getMainFragmentAdapter().getFragmentHost().notifyViewCreated(mMainFragmentAdapter);
        }

        updateAdapter1();
        updateAdapter2();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mGridViewHolder1 = null;
        mGridViewHolder2 = null;
    }

    @Override
    public BrowseSupportFragment.MainFragmentAdapter<Fragment> getMainFragmentAdapter() {
        return mMainFragmentAdapter;
    }

    /**
     * Sets the grid1 selected item position.
     */
    public void setSelectedPosition1(int position) {
        mSelectedPosition1 = position;
        if(mGridViewHolder1 != null && mGridViewHolder1.getGridView().getAdapter() != null) {
            mGridViewHolder1.getGridView().setSelectedPosition(position);
        }
    }

    /**
     * Sets the grid2 selected item position.
     */
    public void setSelectedPosition2(int position) {
        mSelectedPosition2 = position;
        if(mGridViewHolder2 != null && mGridViewHolder2.getGridView().getAdapter() != null) {
            mGridViewHolder2.getGridView().setSelectedPosition(position);
        }
    }

    public int getSelectedPosition1() {
        return mSelectedPosition1;
    }

    public int getSelectedPosition2() {
        return mSelectedPosition2;
    }

    private void updateAdapter1() {
        if (mGridViewHolder1 != null) {
            mGridPresenter1.onBindViewHolder(mGridViewHolder1, mAdapter1);
            if (mSelectedPosition1 != -1) {
                mGridViewHolder1.getGridView().setSelectedPosition(mSelectedPosition1);
            }
        }
    }

    private void updateAdapter2() {
        if (mGridViewHolder2 != null) {
            mGridPresenter1.onBindViewHolder(mGridViewHolder2, mAdapter2);
            if (mSelectedPosition2 != -1) {
                mGridViewHolder2.getGridView().setSelectedPosition(mSelectedPosition2);
            }
        }
    }

    void setEntranceTransitionState(boolean afterTransition) {
        mGridPresenter1.setEntranceTransitionState(mGridViewHolder1, afterTransition);
        mGridPresenter1.setEntranceTransitionState(mGridViewHolder2, afterTransition);
    }
}
