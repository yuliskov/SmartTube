package com.liskovsoft.smartyoutubetv2.tv.ui.browse.grid;

import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.VerticalGridPresenter;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.tv.adapter.VideoGroupObjectAdapter;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.HeaderFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.UriBackgroundManager;

import java.util.ArrayList;
import java.util.List;

public class HeaderGridFragment extends GridFragment implements HeaderFragment {
    private static final String TAG = HeaderGridFragment.class.getSimpleName();
    private static final int COLUMNS = 5;
    private final int ZOOM_FACTOR = FocusHighlight.ZOOM_FACTOR_SMALL;
    private VideoGroupObjectAdapter mGridAdapter;
    private final List<VideoGroup> mPendingUpdates = new ArrayList<>();
    private UriBackgroundManager mBackgroundManager;
    private BrowsePresenter mMainPresenter;
    private boolean mInvalidate;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupEventListeners();
        setupAdapter();
        applyPendingUpdates();

        if (getMainFragmentAdapter().getFragmentHost() != null) {
            getMainFragmentAdapter().getFragmentHost().notifyDataReady(getMainFragmentAdapter());
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        mMainPresenter = BrowsePresenter.instance(context);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mBackgroundManager = ((LeanbackActivity) getActivity()).getBackgroundManager();
    }

    private void setupEventListeners() {
        setOnItemViewClickedListener(new ItemViewClickedListener());
        setOnItemViewSelectedListener(new ItemViewSelectedListener());
    }

    private void applyPendingUpdates() {
        for (VideoGroup group : mPendingUpdates) {
            update(group);
        }

        mPendingUpdates.clear();
    }

    private void setupAdapter() {
        VerticalGridPresenter presenter = new VerticalGridPresenter(ZOOM_FACTOR, false);
        presenter.setNumberOfColumns(COLUMNS);
        setGridPresenter(presenter);

        mGridAdapter = new VideoGroupObjectAdapter();
        setAdapter(mGridAdapter);
    }

    @Override
    public void update(VideoGroup group) {
        if (mGridAdapter == null) {
            mPendingUpdates.add(group);
            return;
        }

        if (mInvalidate) {
            mGridAdapter.clear();
            mInvalidate = false;
        }
        
        mGridAdapter.append(group);
    }

    @Override
    public void clear() {
        mInvalidate = true;
    }

    @Override
    public boolean isEmpty() {
        if (mGridAdapter == null) {
            return false;
        }

        return mGridAdapter.size() == 0;
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {

            if (item instanceof Video) {
                if (getActivity() instanceof LeanbackActivity) {
                    boolean longClick = ((LeanbackActivity) getActivity()).isLongClick();
                    Log.d(TAG, "Is long click: " + longClick);

                    if (longClick) {
                        mMainPresenter.onVideoItemLongClicked((Video) item);
                    } else {
                        mMainPresenter.onVideoItemClicked((Video) item);
                    }
                }
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

            if (index > (size - 15)) {
                mMainPresenter.onScrollEnd(mGridAdapter.getGroup());
            }
        }
    }
}
