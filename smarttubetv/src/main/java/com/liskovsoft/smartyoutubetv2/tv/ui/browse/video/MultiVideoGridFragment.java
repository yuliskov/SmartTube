package com.liskovsoft.smartyoutubetv2.tv.ui.browse.video;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.VerticalGridPresenter;
import androidx.leanback.widget.VerticalGridView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.TickleManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.adapter.HeaderVideoGroupObjectAdapter;
import com.liskovsoft.smartyoutubetv2.tv.adapter.VideoGroupObjectAdapter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.ChannelCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.CustomVerticalGridPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.SearchFieldPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.SearchFieldPresenter.SearchFieldCallback;
import com.liskovsoft.smartyoutubetv2.tv.presenter.VideoCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.base.LongClickPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.base.OnItemLongPressedListener;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces.VideoSection;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.UriBackgroundManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.fragments.MultiGridFragment;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

import java.util.ArrayList;
import java.util.List;

public class MultiVideoGridFragment extends MultiGridFragment implements VideoSection {
    private static final String TAG = MultiVideoGridFragment.class.getSimpleName();
    private HeaderVideoGroupObjectAdapter mGridAdapter1;
    private VideoGroupObjectAdapter mGridAdapter2;
    private final List<VideoGroup> mPendingUpdates1 = new ArrayList<>();
    private final List<VideoGroup> mPendingUpdates2 = new ArrayList<>();
    private UriBackgroundManager mBackgroundManager;
    private VideoGroupPresenter mMainPresenter;
    private LongClickPresenter mCardPresenter1;
    private LongClickPresenter mCardPresenter2;
    private int mSelectedItemIndex1 = -1;
    private int mSelectedItemIndex2 = -1;
    private Video mSelectedItem1;
    private float mVideoGridScale;
    private final Runnable mRestore1Task = this::restorePosition1;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mMainPresenter = getMainPresenter();
        mCardPresenter1 = new ChannelCardPresenter();
        mCardPresenter2 = new VideoCardPresenter();
        mBackgroundManager = ((LeanbackActivity) getActivity()).getBackgroundManager();
        mVideoGridScale = MainUIData.instance(getActivity()).getVideoGridScale();

        setupAdapter();
        setupEventListeners();
        applyPendingUpdates();

        if (getMainFragmentAdapter().getFragmentHost() != null) {
            getMainFragmentAdapter().getFragmentHost().notifyDataReady(getMainFragmentAdapter());
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        removePadding();
    }

    @Override
    public int getPosition() {
        return getSelectedPosition1();
    }

    @Override
    public void setPosition(int index) {
        if (index < 0) {
            return;
        }

        mSelectedItemIndex1 = index;

        if (mGridAdapter1 != null && index < mGridAdapter1.size()) {
            setSelectedPosition1(index);
            mSelectedItemIndex1 = -1;
        }
    }

    @Override
    public void selectItem(Video item) {
        if (item == null) {
            return;
        }

        mSelectedItem1 = item;

        if (mGridAdapter1 != null) {
            int index = mGridAdapter1.indexOfAlt(item);

            if (index != -1) {
                setSelectedPosition1(index);
                mSelectedItem1 = null;
            }
        }
    }

    @Override
    public void update(VideoGroup group) {
        if (group.getPosition() == 0) {
            addSearchHeader();
            freeze1(true);
            updateGroup1(group);
            freeze1(false);
        } else if (group.getPosition() == 1) {
            int action = group.getAction();

            // Attempt to fix: IllegalStateException: Cannot call this method while RecyclerView is computing a layout or scrolling
            if ((action == VideoGroup.ACTION_SYNC || action == VideoGroup.ACTION_REPLACE) && getBrowseGrid2() != null && getBrowseGrid2().isComputingLayout()) {
                return;
            }

            // Smooth remove animation
            if (action == VideoGroup.ACTION_REMOVE) {
                updateGroup2(group);
                return;
            }

            freeze2(true);
            updateGroup2(group);
            freeze2(false);
        }
    }

    /**
     * https://stackoverflow.com/questions/9685658/add-padding-on-view-programmatically
     */
    private void removePadding() {
        VerticalGridView browseGrid = getBrowseGrid1();

        if (browseGrid != null) {
            // Don't remove padding at all. This could cause weird card zooming.
            browseGrid.setPadding(browseGrid.getPaddingLeft(), browseGrid.getPaddingTop(), browseGrid.getPaddingRight() / 3, browseGrid.getPaddingBottom());
        }

        browseGrid = getBrowseGrid2();

        if (browseGrid != null) {
            // Don't remove padding at all. This could cause weird card zooming.
            browseGrid.setPadding(browseGrid.getPaddingLeft() / 3, browseGrid.getPaddingTop(), browseGrid.getPaddingRight(), browseGrid.getPaddingBottom());
        }
    }

    protected VideoGroupPresenter getMainPresenter() {
        return BrowsePresenter.instance(getContext());
    }

    private void setupEventListeners() {
        // We'll use attribute on Video item to differentiate between grids
        setOnItemViewClickedListener1(new ItemViewClickedListener1());
        setOnItemViewSelectedListener1(new ItemViewSelectedListener1());
        setOnItemViewClickedListener2(new ItemViewClickedListener2());
        setOnItemViewSelectedListener2(new ItemViewSelectedListener2());
        mCardPresenter1.setOnItemViewLongPressedListener(new ItemViewLongPressedListener());
        mCardPresenter2.setOnItemViewLongPressedListener(new ItemViewLongPressedListener());
    }

    private void applyPendingUpdates() {
        applyPendingUpdates1();
        applyPendingUpdates2();
    }

    private void applyPendingUpdates1() {
        // prevent modification within update method
        List<VideoGroup> copyArray = new ArrayList<>(mPendingUpdates1);

        mPendingUpdates1.clear();

        for (VideoGroup group : copyArray) {
            update(group);
        }
    }

    private void applyPendingUpdates2() {
        // prevent modification within update method
        List<VideoGroup> copyArray = new ArrayList<>(mPendingUpdates2);

        mPendingUpdates2.clear();

        for (VideoGroup group : copyArray) {
            update(group);
        }
    }

    private void setupAdapter() {
        // Left vertical list of channels
        VerticalGridPresenter presenter1 = new CustomVerticalGridPresenter(R.layout.lb_vertical_grid1, R.id.browse_grid1);
        presenter1.setNumberOfColumns(1);

        // Right grid of channel's content
        VerticalGridPresenter presenter2 = new CustomVerticalGridPresenter(R.layout.lb_vertical_grid2, R.id.browse_grid2);
        int maxColsNum = GridFragmentHelper.getMaxColsNum(getContext(), R.dimen.card_width, mVideoGridScale);
        presenter2.setNumberOfColumns(Math.max(maxColsNum, 1) - 1);

        setGridPresenter1(presenter1);
        setGridPresenter2(presenter2);

        if (mGridAdapter1 == null) {
            ClassPresenterSelector presenterSelector = new ClassPresenterSelector();
            presenterSelector.addClassPresenter(Video.class, mCardPresenter1);
            presenterSelector.addClassPresenter(SearchFieldCallback.class, new SearchFieldPresenter());

            mGridAdapter1 = new HeaderVideoGroupObjectAdapter(presenterSelector);
            setAdapter1(mGridAdapter1);
        }

        if (mGridAdapter2 == null) {
            mGridAdapter2 = new VideoGroupObjectAdapter(mCardPresenter2);
            setAdapter2(mGridAdapter2);
        }
    }

    private void updateGroup1(VideoGroup group) {
        if (mGridAdapter1 == null) {
            mPendingUpdates1.add(group);
            return;
        }

        int action = group.getAction();

        // Clear both because second grid is dependable on first one
        if (action == VideoGroup.ACTION_REPLACE) {
            clear1();
            clear2();
        } else if (action == VideoGroup.ACTION_REMOVE) {
            mGridAdapter1.remove(group);
            return;
        } else if (action == VideoGroup.ACTION_SYNC) {
            mGridAdapter1.sync(group);
            return;
        }

        if (group.isEmpty()) {
            return;
        }

        mGridAdapter1.add(group);

        restorePosition1();
    }

    private void updateGroup2(VideoGroup group) {
        if (mGridAdapter2 == null) {
            mPendingUpdates2.add(group);
            return;
        }

        int action = group.getAction();
        
        if (action == VideoGroup.ACTION_REPLACE) {
            clear2();
        } else if (action == VideoGroup.ACTION_REMOVE) {
            // Remove not supported
            return;
        } else if (action == VideoGroup.ACTION_SYNC) {
            mGridAdapter2.sync(group);
            return;
        }

        if (group.isEmpty()) {
            return;
        }

        mGridAdapter2.add(group);

        // TODO: Do we need to restore position on second group?
        //restorePosition2();
    }

    private void restorePosition1() {
        setPosition(mSelectedItemIndex1);
        selectItem(mSelectedItem1);

        // Item not found? Lookup in next group.
        if (mSelectedItemIndex1 != -1) {
            if (mMainPresenter.hasPendingActions()) {
                TickleManager.instance().runTask(mRestore1Task, 500);
            } else {
                mMainPresenter.onScrollEnd((Video) mGridAdapter1.get(mGridAdapter1.size() - 1));
            }
        }
    }

    @Override
    public void clear() {
        clear1();
        clear2();
    }

    private void clear1() {
        if (mGridAdapter1 != null) {
            mGridAdapter1.setHeader(null);
            mGridAdapter1.clear();
        }
    }

    private void clear2() {
        if (mGridAdapter2 != null) {
            mGridAdapter2.clear();
        }
    }

    private void addSearchHeader() {
        if (mGridAdapter1 == null || mGridAdapter1.getHeader() != null || !MainUIData.instance(getContext()).isChannelsFilterEnabled()) {
            return;
        }

        setPosition(1);

        mGridAdapter1.setHeader(new SearchFieldCallback() {
            @Override
            public void onTextChanged(String text) {
                super.onTextChanged(text);
                mGridAdapter1.filter(text);
            }
        });
    }

    @Override
    public boolean isEmpty() {
        return isEmpty1() && isEmpty2();
    }

    private boolean isEmpty1() {
        if (mGridAdapter1 == null) {
            return mPendingUpdates1.isEmpty();
        }

        return mGridAdapter1.size() == 0;
    }

    private boolean isEmpty2() {
        if (mGridAdapter2 == null) {
            return mPendingUpdates2.isEmpty();
        }

        return mGridAdapter2.size() == 0;
    }

    /**
     * Disable scrolling on partially updated grid. This prevent cards from misbehaving.
     */
    private void freeze1(boolean freeze) {
        if (getBrowseGrid1() != null) {
            getBrowseGrid1().setScrollEnabled(!freeze);
            getBrowseGrid1().setAnimateChildLayout(!freeze);
        }
    }

    /**
     * Disable scrolling on partially updated grid. This prevent cards from misbehaving.
     */
    private void freeze2(boolean freeze) {
        if (getBrowseGrid2() != null) {
            getBrowseGrid2().setScrollEnabled(!freeze);
            getBrowseGrid2().setAnimateChildLayout(!freeze);
        }
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

    private final class ItemViewClickedListener2 implements androidx.leanback.widget.OnItemViewClickedListener {
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

    private final class ItemViewClickedListener1 implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                   RowPresenter.ViewHolder rowViewHolder, Row row) {
            if (item instanceof Video) {
                mMainPresenter.onVideoItemClicked((Video) item);
            }
        }
    }

    private final class ItemViewSelectedListener1 implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                   RowPresenter.ViewHolder rowViewHolder, Row row) {
            if (item instanceof Video) {
                mMainPresenter.onVideoItemSelected((Video) item);

                // Not working: Exception: Cannot call this method while RecyclerView is computing a layout
                // Change unwatched state (remove red mark).
                // Possible this isn't what you want. Red mark improve navigation inside the channel list.
                //((Video) item).hasNewContent = false;
                //mGridAdapter1.notifyItemRangeChanged(mGridAdapter1.indexOf((Video) item), 1);
            }
        }
    }

    private final class ItemViewSelectedListener2 implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                   RowPresenter.ViewHolder rowViewHolder, Row row) {
            if (item instanceof Video) {
                mBackgroundManager.setBackgroundFrom((Video) item);

                checkScrollEnd((Video) item);
            }
        }

        private void checkScrollEnd(Video item) {
            VideoGroupObjectAdapter adapter = mGridAdapter2;

            int size = adapter.size();
            int index = adapter.indexOf(item);

            if (index > (size - ViewUtil.GRID_SCROLL_CONTINUE_NUM)) {
                mMainPresenter.onScrollEnd((Video) adapter.get(size - 1));
            }
        }
    }
}
