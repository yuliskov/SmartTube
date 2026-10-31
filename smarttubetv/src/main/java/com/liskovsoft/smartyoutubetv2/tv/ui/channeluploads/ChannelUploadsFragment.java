package com.liskovsoft.smartyoutubetv2.tv.ui.channeluploads;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.VerticalGridView;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelUploadsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.video.VideoGridFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc.ProgressBarManager;

public class ChannelUploadsFragment extends VideoGridFragment implements ChannelUploadsView {
    private static final String SELECTED_ITEM_INDEX = "SelectedItemIndex";
    private ProgressBarManager mProgressBarManager;
    private ChannelUploadsPresenter mChannelUploadsPresenter;
    private boolean mIsFragmentCreated;
    private int mRestoredItemIndex = -1;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(null); // Real restore takes place in the presenter

        mRestoredItemIndex = savedInstanceState != null ? savedInstanceState.getInt(SELECTED_ITEM_INDEX, -1) : -1;
        mIsFragmentCreated = true;
        mChannelUploadsPresenter = ChannelUploadsPresenter.instance(getContext());
        mChannelUploadsPresenter.setView(this);

        mProgressBarManager = new ProgressBarManager();
    }

    @Override
    protected VideoGroupPresenter getMainPresenter() {
        return ChannelUploadsPresenter.instance(getContext());
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // Not robust. Because tab content often changed after reloading.
        outState.putInt(SELECTED_ITEM_INDEX, getPosition());
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Don't move to onCreateView
        mProgressBarManager.setRootView((ViewGroup) getActivity().findViewById(android.R.id.content).getRootView());

        mChannelUploadsPresenter.onViewInitialized();

        // Restore state after crash
        setPosition(mRestoredItemIndex);
        mRestoredItemIndex = -1;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        removeTopPadding();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mChannelUploadsPresenter.onViewDestroyed();
    }

    public void onFinish() {
        mChannelUploadsPresenter.onFinish();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!mIsFragmentCreated) {
            mChannelUploadsPresenter.onViewResumed();
        }

        mIsFragmentCreated = false;
    }

    @Override
    public void showProgressBar(boolean show) {
        if (show) {
            mProgressBarManager.show();
        } else {
            mProgressBarManager.hide();
        }
    }

    /**
     * https://stackoverflow.com/questions/9685658/add-padding-on-view-programmatically
     */
    private void removeTopPadding() {
        VerticalGridView browseGrid = getBrowseGrid();

        if (browseGrid == null) {
            return;
        }

        browseGrid.setPadding(browseGrid.getPaddingLeft(), browseGrid.getPaddingTop() / 3, browseGrid.getPaddingRight(), browseGrid.getPaddingBottom());
    }
}
