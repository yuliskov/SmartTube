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
import com.liskovsoft.smartyoutubetv2.common.misc.TickleManager;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.adapter.VideoGroupObjectAdapter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.VideoCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.CustomVerticalGridPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.base.OnItemViewPressedListener;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces.VideoCategoryFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.UriBackgroundManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.fragments.GridFragment;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

import java.util.ArrayList;
import java.util.List;

public class VideoGridFragment extends GridFragment implements VideoCategoryFragment {
    private static final String TAG = VideoGridFragment.class.getSimpleName();
    private VideoGroupObjectAdapter mGridAdapter;
    private final List<VideoGroup> mPendingUpdates = new ArrayList<>();
    private UriBackgroundManager mBackgroundManager;
    private VideoGroupPresenter mMainPresenter;
    private VideoCardPresenter mCardPresenter;
    private int mSelectedItemIndex = -1;
    private float mVideoGridScale;
    private final Runnable mRestoreTask = this::restorePosition;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mMainPresenter = getMainPresenter();
        mCardPresenter = new VideoCardPresenter();
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
        mCardPresenter.setOnItemViewLongPressedListener(new ItemViewLongClickedListener());
        mCardPresenter.setOnItemViewMenuPressedListener(new ItemViewLongClickedListener());
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
        presenter.setNumberOfColumns(GridFragmentHelper.getMaxColsNum(getContext(), R.dimen.card_width, mVideoGridScale));
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

        if (mGridAdapter != null && index < mGridAdapter.size()) {
            setSelectedPosition(index);
            mSelectedItemIndex = -1;
        } else {
            mSelectedItemIndex = index;
        }
    }

    @Override
    public void update(VideoGroup group) {
        if (mGridAdapter == null) {
            mPendingUpdates.add(group);
            return;
        }

        if (group.isNew()) {
            clear();
        }

        if (group.isEmpty()) {
            return;
        }

        mGridAdapter.append(group);

        restorePosition();
    }

    private void restorePosition() {
        setPosition(mSelectedItemIndex);

        // Item not found? Lookup item in next group.
        if (mSelectedItemIndex != -1) {
            if (mMainPresenter.hasPendingActions()) {
                TickleManager.instance().runTask(mRestoreTask, 500);
            } else {
                mMainPresenter.onScrollEnd((Video) mGridAdapter.get(mGridAdapter.size() - 1));
            }
        }
    }

    @Override
    public void clear() {
        if (mGridAdapter != null) {
            mGridAdapter.clear();
        }
    }

    @Override
    public boolean isEmpty() {
        if (mGridAdapter == null) {
            return mPendingUpdates.isEmpty();
        }

        return mGridAdapter.size() == 0;
    }

    private final class ItemViewLongClickedListener implements OnItemViewPressedListener {
        @Override
        public void onItemPressed(Presenter.ViewHolder itemViewHolder, Object item) {
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
            int size = mGridAdapter.size();
            int index = mGridAdapter.indexOf(item);

            if (index > (size - ViewUtil.GRID_SCROLL_CONTINUE_NUM)) {
                mMainPresenter.onScrollEnd((Video) mGridAdapter.get(size - 1));
            }
        }
    }
}
