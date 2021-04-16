package com.liskovsoft.smartyoutubetv2.tv.ui.browse.video;

import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.VerticalGridPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.adapter.VideoGroupObjectAdapter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.CardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.CustomVerticalGridPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.base.OnItemViewClickedListener;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces.VideoCategoryFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.UriBackgroundManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.fragments.MultiGridFragment;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

import java.util.ArrayList;
import java.util.List;

public class MultiVideoGridFragment extends MultiGridFragment implements VideoCategoryFragment {
    private static final String TAG = MultiVideoGridFragment.class.getSimpleName();
    private VideoGroupObjectAdapter mGridAdapter1;
    private VideoGroupObjectAdapter mGridAdapter2;
    private final List<VideoGroup> mPendingUpdates1 = new ArrayList<>();
    private final List<VideoGroup> mPendingUpdates2 = new ArrayList<>();
    private UriBackgroundManager mBackgroundManager;
    private VideoGroupPresenter mMainPresenter;
    private CardPresenter mCardPresenter;
    private int mSelectedItemIndex1 = -1;
    private int mSelectedItemIndex2 = -1;
    private float mVideoGridScale;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mMainPresenter = getMainPresenter();
        mCardPresenter = new CardPresenter();
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
        // We'll use attribute on Video item to differentiate between grids
        setOnItemViewClickedListener1(new ItemViewClickedListener());
        setOnItemViewSelectedListener1(new ItemViewSelectedListener());
        setOnItemViewClickedListener2(new ItemViewClickedListener());
        setOnItemViewSelectedListener2(new ItemViewSelectedListener());
        mCardPresenter.setOnLongClickedListener(new ItemViewLongClickedListener());
        mCardPresenter.setOnMenuPressedListener(new ItemViewLongClickedListener());
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
        presenter2.setNumberOfColumns(GridFragmentHelper.getMaxColsNum(getContext(), R.dimen.card_width, mVideoGridScale) - 1);

        setGridPresenter1(presenter1);
        setGridPresenter2(presenter2);

        if (mGridAdapter1 == null) {
            mGridAdapter1 = new VideoGroupObjectAdapter(mCardPresenter);
            setAdapter1(mGridAdapter1);
        }

        if (mGridAdapter2 == null) {
            mGridAdapter2 = new VideoGroupObjectAdapter(mCardPresenter);
            setAdapter2(mGridAdapter2);
        }
    }

    @Override
    public int getPosition() {
        // TODO: getPosition2 not used
        return getSelectedPosition1();
    }

    @Override
    public void setPosition(int index) {
        if (index < 0) {
            return;
        }

        if (mGridAdapter1 != null && index < mGridAdapter1.size()) {
            // TODO: setPosition2 not used
            setSelectedPosition1(index);
            mSelectedItemIndex1 = -1;
        } else {
            mSelectedItemIndex1 = index;
        }
    }

    @Override
    public void update(VideoGroup group) {
        if (group.getPosition() == 0) {
            updateGroup1(group);
        } else if (group.getPosition() == 1) {
            updateGroup2(group);
        }
    }

    private void updateGroup1(VideoGroup group) {
        if (mGridAdapter1 == null) {
            mPendingUpdates1.add(group);
            return;
        }

        if (group.isBegin()) {
            clear1();
        }

        if (group.isEmpty()) {
            return;
        }

        mGridAdapter1.append(group);

        updatePosition1();
    }

    private void updateGroup2(VideoGroup group) {
        if (mGridAdapter2 == null) {
            mPendingUpdates2.add(group);
            return;
        }

        if (group.isBegin()) {
            clear2();
        }

        if (group.isEmpty()) {
            return;
        }

        mGridAdapter2.append(group);

        // TODO: Do we need to update position on second group?
        //updatePosition2();
    }

    private void updatePosition1() {
        setPosition(mSelectedItemIndex1);

        // Item not found? Load next group.
        if (mSelectedItemIndex1 != -1) {
            mMainPresenter.onScrollEnd(mGridAdapter1.getLastGroup());
        }
    }

    private void clear1() {
        if (mGridAdapter1 != null) {
            mGridAdapter1.clear();
        }
    }

    @Override
    public void clear() {
        clear1();
        clear2();
    }

    private void clear2() {
        if (mGridAdapter2 != null) {
            mGridAdapter2.clear();
        }
    }

    @Override
    public boolean isEmpty() {
        if (mGridAdapter1 == null) {
            return false;
        }

        return mGridAdapter1.size() == 0;
    }

    private final class ItemViewLongClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemViewClicked(Presenter.ViewHolder itemViewHolder, Object item) {
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

                checkScrollEnd((Video) item);
            }
        }

        private void checkScrollEnd(Video item) {
            int size = mGridAdapter1.size();
            int index = mGridAdapter1.indexOf(item);

            if (index > (size - ViewUtil.GRID_SCROLL_CONTINUE_NUM)) {
                mMainPresenter.onScrollEnd(mGridAdapter1.getLastGroup());
            }
        }
    }
}
