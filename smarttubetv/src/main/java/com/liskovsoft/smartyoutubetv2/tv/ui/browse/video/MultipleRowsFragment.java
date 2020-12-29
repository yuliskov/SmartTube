package com.liskovsoft.smartyoutubetv2.tv.ui.browse.video;

import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.tv.adapter.VideoGroupObjectAdapter;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces.VideoCategoryFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.UriBackgroundManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class MultipleRowsFragment extends RowsSupportFragment implements VideoCategoryFragment {
    private static final String TAG = MultipleRowsFragment.class.getSimpleName();
    private static final int ZOOM_FACTOR = FocusHighlight.ZOOM_FACTOR_SMALL;
    private static final boolean USE_FOCUS_DIMMER = false;
    private UriBackgroundManager mBackgroundManager;
    private ArrayObjectAdapter mRowsAdapter;
    private Map<Integer, VideoGroupObjectAdapter> mVideoGroupAdapters;
    private final List<VideoGroup> mPendingUpdates = new ArrayList<>();
    private VideoGroupPresenter mMainPresenter;
    private boolean mInvalidate;
    private int mSelectedRowIndex = -1;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mMainPresenter = getMainPresenter();
        mBackgroundManager = ((LeanbackActivity) getActivity()).getBackgroundManager();

        setupAdapter();
        setupEventListeners();
        applyPendingUpdates();
    }

    protected abstract VideoGroupPresenter getMainPresenter();

    private void applyPendingUpdates() {
        for (VideoGroup group : mPendingUpdates) {
            update(group);
        }

        mPendingUpdates.clear();
    }

    private void setupAdapter() {
        if (mVideoGroupAdapters == null) {
            mVideoGroupAdapters = new HashMap<>();
        }

        if (mRowsAdapter == null) {
            mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter(ZOOM_FACTOR, USE_FOCUS_DIMMER));
            setAdapter(mRowsAdapter);
        }
    }

    private void setupEventListeners() {
        setOnItemViewClickedListener(new ItemViewClickedListener());
        setOnItemViewSelectedListener(new ItemViewSelectedListener());
    }

    @Override
    public void invalidate() {
        mInvalidate = true;
    }

    @Override
    public void clear() {
        if (mRowsAdapter != null) {
            mRowsAdapter.clear();
        }

        if (mVideoGroupAdapters != null) {
            mVideoGroupAdapters.clear();
        }
    }

    @Override
    public boolean isEmpty() {
        if (mRowsAdapter == null) {
            return false;
        }

        return mRowsAdapter.size() == 0;
    }

    @Override
    public void update(VideoGroup group) {
        if (mVideoGroupAdapters == null) {
            mPendingUpdates.add(group);
            return;
        }

        if (mInvalidate) {
            clear();
            mInvalidate = false;
        }

        HeaderItem rowHeader = new HeaderItem(group.getTitle());
        int mediaGroupId = group.getId(); // Create unique int from category.

        VideoGroupObjectAdapter existingAdapter = mVideoGroupAdapters.get(mediaGroupId);

        if (existingAdapter == null) {
            VideoGroupObjectAdapter mediaGroupAdapter = new VideoGroupObjectAdapter(group);

            mVideoGroupAdapters.put(mediaGroupId, mediaGroupAdapter);

            ListRow row = new ListRow(rowHeader, mediaGroupAdapter);
            mRowsAdapter.add(row);
        } else {
            Log.d(TAG, "Continue row %s %s", group.getTitle(), System.currentTimeMillis());
            existingAdapter.append(group); // continue row
        }
        
        setPosition(mSelectedRowIndex);
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

        if (mRowsAdapter != null && index < mRowsAdapter.size()) {
            setSelectedPosition(index);
            mSelectedRowIndex = -1;
        } else {
            mSelectedRowIndex = index;
        }
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

                checkScrollEnd((Video)item);
            }
        }

        private void checkScrollEnd(Video item) {
            for (VideoGroupObjectAdapter adapter : mVideoGroupAdapters.values()) {
                int index = adapter.indexOf(item);

                if (index != -1) {
                    int size = adapter.size();
                    if (index > (size - 4)) {
                        mMainPresenter.onScrollEnd(adapter.getGroup());
                    }
                    break;
                }
            }
        }
    }
}
