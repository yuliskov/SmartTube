package com.liskovsoft.smartyoutubetv2.tv.ui.modern.channel;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.leanback.widget.SinglePresenterSelector;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelUploadsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.browse.ModernGridView;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernColorScheme;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernSpinner;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.widgets.ModernCardPresenter;

/**
 * Uploads of a channel opened from the channels section: grid of tiles.
 */
public class ModernChannelUploadsFragment extends Fragment implements ChannelUploadsView {
    private static final String SELECTED_ITEM_INDEX = "SelectedItemIndex";
    private ChannelUploadsPresenter mPresenter;
    private ModernGridView mGrid;
    private View mProgress;
    private boolean mIsFragmentCreated;
    private int mRestoredItemIndex = -1;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(null);
        mRestoredItemIndex = savedInstanceState != null ? savedInstanceState.getInt(SELECTED_ITEM_INDEX, -1) : -1;
        mIsFragmentCreated = true;
        mPresenter = ChannelUploadsPresenter.instance(getContext());
        mPresenter.setView(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FrameLayout root = new FrameLayout(requireContext());
        root.setBackgroundColor(ModernColorScheme.background(requireContext()));

        ModernCardPresenter cardPresenter = new ModernCardPresenter();
        cardPresenter.setOnItemViewLongPressedListener((viewHolder, item) -> {
            if (item instanceof Video) {
                mPresenter.onVideoItemLongClicked((Video) item);
            }
        });
        mGrid = new ModernGridView(requireContext(), mPresenter, new SinglePresenterSelector(cardPresenter));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.topMargin = getResources().getDimensionPixelSize(R.dimen.modern_first_shelf_y);
        root.addView(mGrid, params);

        ProgressBar progress = ModernSpinner.create(requireContext());
        progress.setVisibility(View.GONE);
        int spinnerSize = getResources().getDimensionPixelSize(R.dimen.modern_spinner_size);
        root.addView(progress, new FrameLayout.LayoutParams(spinnerSize, spinnerSize, android.view.Gravity.CENTER));
        mProgress = progress;

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mPresenter.onViewInitialized();
        mGrid.setPosition(mRestoredItemIndex);
        mRestoredItemIndex = -1;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SELECTED_ITEM_INDEX, mGrid.getPosition());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!mIsFragmentCreated) {
            mPresenter.onViewResumed();
        }
        mIsFragmentCreated = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mPresenter.onViewDestroyed();
    }

    public void onFinish() {
        mPresenter.onFinish();
    }

    @Override
    public void update(VideoGroup videoGroup) {
        mGrid.update(videoGroup);
        if (!mGrid.hasFocus()) {
            mGrid.focusContent();
        }
    }

    @Override
    public void showProgressBar(boolean show) {
        if (mProgress != null) {
            mProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void clear() {
        mGrid.clear();
    }
}
