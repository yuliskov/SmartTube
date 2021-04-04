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
    private final List<VideoGroup> mPendingUpdates = new ArrayList<>();
    private UriBackgroundManager mBackgroundManager;
    private VideoGroupPresenter mMainPresenter;
    private CardPresenter mCardPresenter;
    private boolean mInvalidate;
    private int mSelectedItemIndex = -1;
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
        setOnItemViewClickedListener1(new ItemViewClickedListener1());
        setOnItemViewSelectedListener1(new ItemViewSelectedListener1());
        setOnItemViewClickedListener2(new ItemViewClickedListener2());
        setOnItemViewSelectedListener2(new ItemViewSelectedListener2());
        mCardPresenter.setOnLongClickedListener(new ItemViewLongClickedListener());
        mCardPresenter.setOnMenuPressedListener(new ItemViewLongClickedListener());
    }

    private void applyPendingUpdates() {
        for (VideoGroup group : mPendingUpdates) {
            update(group);
        }

        mPendingUpdates.clear();
    }

    private void setupAdapter() {
        VerticalGridPresenter presenter = new CustomVerticalGridPresenter();
        presenter.setNumberOfColumns(GridFragmentHelper.getColumnsNum(getContext(), R.dimen.card_width, mVideoGridScale));
        // TODO: may need to use different presenters
        setGridPresenter1(presenter);
        setGridPresenter2(presenter);

        if (mGridAdapter1 == null) {
            mGridAdapter1 = new VideoGroupObjectAdapter(mCardPresenter);
            setAdapter1(mGridAdapter1);
        }

        if (mGridAdapter2 == null) {
            mGridAdapter2 = new VideoGroupObjectAdapter(mCardPresenter);
            setAdapter1(mGridAdapter2);
        }
    }

    @Override
    public int getPosition() {
        // TODO: getPosition1 not used
        return getSelectedPosition2();
    }

    @Override
    public void setPosition(int index) {
        if (index < 0) {
            return;
        }

        if (mGridAdapter1 != null && index < mGridAdapter1.size()) {
            // TODO: setPosition1 not used
            setSelectedPosition2(index);
            mSelectedItemIndex = -1;
        } else {
            mSelectedItemIndex = index;
        }
    }

    @Override
    public void update(VideoGroup group) {
        if (mGridAdapter1 == null) {
            mPendingUpdates.add(group);
            return;
        }

        if (mInvalidate) {
            clear();
            mInvalidate = false;
        }
        
        mGridAdapter1.append(group);

        updatePosition();
    }

    private void updatePosition() {
        setPosition(mSelectedItemIndex);

        // Item not found? Load next group.
        if (mSelectedItemIndex != -1) {
            mMainPresenter.onScrollEnd(mGridAdapter1.getLastGroup());
        }
    }

    @Override
    public void invalidate() {
        mInvalidate = true;
    }

    @Override
    public void clear() {
        if (mGridAdapter1 != null) {
            mGridAdapter1.clear();
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

    private final class ItemViewClickedListener1 implements androidx.leanback.widget.OnItemViewClickedListener {
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

    private final class ItemViewSelectedListener1 implements OnItemViewSelectedListener {
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
            int size = mGridAdapter1.size();
            int index = mGridAdapter1.indexOf(item);

            if (index > (size - ViewUtil.GRID_SCROLL_CONTINUE_NUM)) {
                mMainPresenter.onScrollEnd(mGridAdapter1.getLastGroup());
            }
        }
    }
}
