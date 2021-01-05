package com.liskovsoft.smartyoutubetv2.tv.ui.browse.video;

import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.leanback.widget.FocusHighlight;
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
import com.liskovsoft.smartyoutubetv2.tv.presenter.base.OnItemViewClickedListener;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces.VideoCategoryFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.UriBackgroundManager;

import java.util.ArrayList;
import java.util.List;

public class VideoGridFragment extends AutoSizeGridFragment implements VideoCategoryFragment {
    private static final String TAG = VideoGridFragment.class.getSimpleName();
    private static final int ZOOM_FACTOR = FocusHighlight.ZOOM_FACTOR_SMALL;
    private static final boolean USE_FOCUS_DIMMER = false;
    private static final int CHECK_SCROLL_ITEMS_NUM = 15;
    private VideoGroupObjectAdapter mGridAdapter;
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
        setOnItemViewClickedListener(new ItemViewClickedListener());
        setOnItemViewSelectedListener(new ItemViewSelectedListener());
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
        VerticalGridPresenter presenter = new VerticalGridPresenter(ZOOM_FACTOR, USE_FOCUS_DIMMER);
        presenter.setNumberOfColumns(getColumnsNum(R.dimen.card_width, mVideoGridScale));
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

        if (mInvalidate) {
            clear();
            mInvalidate = false;
        }
        
        mGridAdapter.append(group);

        setPosition(mSelectedItemIndex);
    }

    @Override
    public void invalidate() {
        mInvalidate = true;
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
            return false;
        }

        return mGridAdapter.size() == 0;
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
            int size = mGridAdapter.size();
            int index = mGridAdapter.indexOf(item);

            if (index > (size - CHECK_SCROLL_ITEMS_NUM)) {
                mMainPresenter.onScrollEnd(mGridAdapter.getGroup());
            }
        }
    }
}
