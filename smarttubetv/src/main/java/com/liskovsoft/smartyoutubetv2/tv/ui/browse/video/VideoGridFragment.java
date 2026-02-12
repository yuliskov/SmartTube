package com.liskovsoft.smartyoutubetv2.tv.ui.browse.video;

import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.VerticalGridPresenter;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.TickleManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.LoadingManager;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.adapter.VideoGroupObjectAdapter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.CustomVerticalGridPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.ShortsCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.VideoCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.base.OnItemLongPressedListener;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces.VideoSection;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.UriBackgroundManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.fragments.GridFragment;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

import java.util.ArrayList;
import java.util.List;

public class VideoGridFragment extends GridFragment implements VideoSection {
    private static final String TAG = VideoGridFragment.class.getSimpleName();
    private static final int RESTORE_MAX_SIZE = 10_000;
    private VideoGroupObjectAdapter mGridAdapter;
    private final List<VideoGroup> mPendingUpdates = new ArrayList<>();
    private UriBackgroundManager mBackgroundManager;
    private VideoGroupPresenter mMainPresenter;
    private VideoCardPresenter mCardPresenter;
    private int mSelectedItemIndex = -1;
    private Video mSelectedItem;
    private float mVideoGridScale;
    private final Runnable mRestoreTask = this::restorePosition;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mMainPresenter = getMainPresenter();
        mCardPresenter = isShorts() ? new ShortsCardPresenter() : new VideoCardPresenter();
        mBackgroundManager = ((LeanbackActivity) getActivity()).getBackgroundManager();
        mVideoGridScale = MainUIData.instance(getActivity()).getVideoGridScale();

        setupAdapter();
        setupEventListeners();
        applyPendingUpdates();

        if (getMainFragmentAdapter().getFragmentHost() != null) {
            getMainFragmentAdapter().getFragmentHost().notifyDataReady(getMainFragmentAdapter());
        }
    }

    protected VideoGroupPresenter getMainPresenter() {
        return BrowsePresenter.instance(getContext());
    }

    private void setupEventListeners() {
        setOnItemViewClickedListener(new ItemViewClickedListener());
        setOnItemViewSelectedListener(new ItemViewSelectedListener());
        mCardPresenter.setOnItemViewLongPressedListener(new ItemViewLongPressedListener());
    }

    private void applyPendingUpdates() {
        // prevent modification within update method
        List<VideoGroup> copyArray = new ArrayList<>(mPendingUpdates);

        mPendingUpdates.clear();

        for (VideoGroup group : copyArray) {
            update(group);
        }
    }

    private void setupAdapter() {
        VerticalGridPresenter presenter = new CustomVerticalGridPresenter();
        presenter.setNumberOfColumns(
                GridFragmentHelper.getMaxColsNum(getContext(), isShorts() ? R.dimen.shorts_card_width : R.dimen.card_width, mVideoGridScale)
        );
        setGridPresenter(presenter);

        if (mGridAdapter == null) {
            mGridAdapter = new VideoGroupObjectAdapter(mCardPresenter);
            setAdapter(mGridAdapter);
        }
    }

    @Override
    public int getPosition() {
        return getSelectedPosition();
    }

    @Override
    public void setPosition(int index) {
        if (index < 0) {
            return;
        }

        mSelectedItemIndex = index;
        mSelectedItem = null;

        if (mGridAdapter != null && index < mGridAdapter.size()) {
            setSelectedPosition(index);
            mSelectedItemIndex = -1;
        }
    }

    @Override
    public void selectItem(Video item) {
        if (item == null) {
            return;
        }

        mSelectedItem = item;
        mSelectedItemIndex = -1;

        if (mGridAdapter != null) {
            int index = mGridAdapter.indexOfAlt(item);

            if (index != -1) {
                setSelectedPosition(index);
                mSelectedItem = null;
            }
        }
    }

    @Override
    public void update(VideoGroup group) {
        int action = group.getAction();

        // Attempt to fix: IllegalStateException: Cannot call this method while RecyclerView is computing a layout or scrolling
        if ((action == VideoGroup.ACTION_SYNC || action == VideoGroup.ACTION_REPLACE) && getBrowseGrid() != null && getBrowseGrid().isComputingLayout()) {
            return;
        }

        // Smooth remove animation
        if (action == VideoGroup.ACTION_REMOVE || action == VideoGroup.ACTION_REMOVE_AUTHOR) {
            updateInt(group);
            return;
        }

        freeze(true);

        updateInt(group);

        freeze(false);
    }

    private void updateInt(VideoGroup group) {
        if (mGridAdapter == null) {
            mPendingUpdates.add(group);
            return;
        }

        int action = group.getAction();

        if (action == VideoGroup.ACTION_REPLACE) {
            clear();
        } else if (action == VideoGroup.ACTION_REMOVE) {
            mGridAdapter.remove(group);
            return;
        } else if (action == VideoGroup.ACTION_REMOVE_AUTHOR) {
            mGridAdapter.removeAuthor(group);
            return;
        } else if (action == VideoGroup.ACTION_SYNC) {
            mGridAdapter.sync(group);
            return;
        }

        if (group.isEmpty()) {
            return;
        }

        mGridAdapter.add(group);

        restorePosition();
    }

    private void restorePosition() {
        LoadingManager.showLoading(getContext(), true); // Restore task takes some time

        setPosition(mSelectedItemIndex);
        selectItem(mSelectedItem);

        if ((mSelectedItemIndex == -1 && mSelectedItem == null) || mGridAdapter == null || mGridAdapter.size() > RESTORE_MAX_SIZE) {
            LoadingManager.showLoading(getContext(), false);
            return;
        }

        // Item not found? Lookup item in next group.
        if (mMainPresenter.hasPendingActions()) {
            TickleManager.instance().runTask(mRestoreTask, 500);
        } else {
            mMainPresenter.onScrollEnd((Video) mGridAdapter.get(mGridAdapter.size() - 1));
        }
    }

    /**
     * Disable scrolling on partially updated grid. This shouldn't fix card position bug on Android 4.4.
     */
    private void freeze(boolean freeze) {
        if (getBrowseGrid() != null) {
            getBrowseGrid().setScrollEnabled(!freeze);
            getBrowseGrid().setAnimateChildLayout(!freeze);
        }
    }

    @Override
    public void clear() {
        if (mGridAdapter != null) {
            // Fix: Invalid item position -1(-1). Item count:84 androidx.leanback.widget.VerticalGridView
            freeze(true);

            mGridAdapter.clear();
        }
    }

    @Override
    public boolean isEmpty() {
        if (mGridAdapter == null) {
            return mPendingUpdates.isEmpty();
        }

        return mGridAdapter.isEmpty();
    }

    protected boolean isShorts() {
        return false;
    }

    private final class ItemViewLongPressedListener implements OnItemLongPressedListener {
        @Override
        public void onItemLongPressed(Presenter.ViewHolder itemViewHolder, Object item) {
            if (item instanceof Video) {
                mMainPresenter.onVideoItemLongClicked((Video) item);
            } else {
                Toast.makeText(getActivity(), item.toString(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private final class ItemViewClickedListener implements androidx.leanback.widget.OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {

            if (item instanceof Video) {
                mMainPresenter.onVideoItemClicked((Video) item);
            } else {
                Toast.makeText(getActivity(), item.toString(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                   RowPresenter.ViewHolder rowViewHolder, Row row) {
            if (item instanceof Video) {
                mBackgroundManager.setBackgroundFrom((Video) item);

                mMainPresenter.onVideoItemSelected((Video) item);

                checkScrollEnd((Video) item);
            }
        }

        private void checkScrollEnd(Video item) {
            int size = mGridAdapter.size();
            int index = mGridAdapter.indexOf(item);

            if (index > (size - (isShorts() ? ViewUtil.GRID_SCROLL_CONTINUE_NUM * 2 : ViewUtil.GRID_SCROLL_CONTINUE_NUM))) {
                mMainPresenter.onScrollEnd((Video) mGridAdapter.get(size - 1));
            }
        }
    }
}
