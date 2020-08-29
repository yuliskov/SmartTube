package com.liskovsoft.smartyoutubetv2.tv.ui.main.grid;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.VerticalGridPresenter;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.Video;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.mvp.presenters.MainPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.CardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.base.UriBackgroundManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.main.MainActivity;

import java.util.ArrayList;
import java.util.List;

public class GridHeaderFragment extends GridFragment {
    private static final String TAG = GridHeaderFragment.class.getSimpleName();
    private static final int COLUMNS = 5;
    private final int ZOOM_FACTOR = FocusHighlight.ZOOM_FACTOR_SMALL;
    private ArrayObjectAdapter mAdapter;
    private final List<VideoGroup> mPendingUpdates = new ArrayList<>();
    private UriBackgroundManager mBackgroundManager;
    private MainPresenter mMainPresenter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupEventListeners();
        setupAdapter();
        applyPendingUpdates();
        getMainFragmentAdapter().getFragmentHost().notifyDataReady(getMainFragmentAdapter());
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        mBackgroundManager = UriBackgroundManager.instance(getActivity());
        mMainPresenter = MainPresenter.instance(context);
    }

    private void setupEventListeners() {
        setOnItemViewClickedListener(new ItemViewClickedListener());
        setOnItemViewSelectedListener(new ItemViewSelectedListener());
    }

    private void applyPendingUpdates() {
        for (VideoGroup group : mPendingUpdates) {
            updateGrid(group);
        }

        mPendingUpdates.clear();
    }

    private void setupAdapter() {
        VerticalGridPresenter presenter = new VerticalGridPresenter(ZOOM_FACTOR);
        presenter.setNumberOfColumns(COLUMNS);
        setGridPresenter(presenter);

        //CardPresenterSelector cardPresenter = new CardPresenterSelector(getActivity());
        mAdapter = new ArrayObjectAdapter(new CardPresenter());
        setAdapter(mAdapter);
    }

    public void updateGrid(VideoGroup group) {
        if (mAdapter == null) {
            mPendingUpdates.add(group);
            return;
        }
        
        mAdapter.addAll(mAdapter.size(), group.getVideos());
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {

            if (item instanceof Video) {
                if (getActivity() instanceof MainActivity) {
                    boolean longClick = ((MainActivity) getActivity()).isLongClick();
                    Log.d(TAG, "Is long click: " + longClick);

                    if (longClick) {
                        mMainPresenter.onVideoItemLongPressed((Video) item);
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
                Uri backgroundURI = Uri.parse(((Video) item).bgImageUrl);
                mBackgroundManager.startBackgroundTimer(backgroundURI);
            }
        }
    }
}
