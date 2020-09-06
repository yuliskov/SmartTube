package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import androidx.fragment.app.Fragment;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.Row;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.BrowseFragment.HeaderViewSelectedListener;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.grid.GridHeaderFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.grid.GridHeaderItem;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.row.RowHeaderFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.row.RowHeaderItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PageRowFragmentFactory extends BrowseSupportFragment.FragmentFactory<Fragment> {
    private static final String TAG = PageRowFragmentFactory.class.getSimpleName();
    private final BackgroundManager mBackgroundManager;
    private final Map<Integer, Fragment> mFragments;
    private final Map<Integer, List<VideoGroup>> mPendingUpdates;
    private final HeaderViewSelectedListener mViewSelectedListener;

    public PageRowFragmentFactory(BackgroundManager backgroundManager) {
        this(backgroundManager, null);
    }

    public PageRowFragmentFactory(BackgroundManager backgroundManager, HeaderViewSelectedListener viewSelectedListener) {
        mBackgroundManager = backgroundManager;
        mViewSelectedListener = viewSelectedListener;
        mFragments = new HashMap<>();
        mPendingUpdates = new HashMap<>();
    }

    @Override
    public Fragment createFragment(Object rowObj) {
        Log.d(TAG, "Creating PageRow fragment");

        Row row = (Row) rowObj;

        if (mBackgroundManager != null) {
            mBackgroundManager.setDrawable(null);
        }

        HeaderItem header = row.getHeaderItem();
        Fragment fragment = null;

        if (header instanceof RowHeaderItem) {
            fragment = new RowHeaderFragment();
        } else if (header instanceof GridHeaderItem) {
            fragment = new GridHeaderFragment();
        }

        if (fragment != null) {
            mFragments.put((int) header.getId(), fragment);

            // give a chance to clear pending updates
            if (mViewSelectedListener != null) {
                mViewSelectedListener.onHeaderSelected(null, row);
            }

            updateFromPending(fragment, (int) header.getId());

            return fragment;
        }

        throw new IllegalArgumentException(String.format("Invalid row %s", rowObj));
    }

    public void updateFragment(VideoGroup group) {
        if (group == null || group.isEmpty()) {
            return;
        }

        int headerId = group.getHeader().getId();

        Fragment fragment = mFragments.get(headerId);

        addToPending(group, headerId);

        if (fragment == null) {
            Log.d(TAG, "Page row fragment not initialized for group: " + group.getTitle());

            return;
        }

        updateFragment(fragment, group);
    }

    private void updateFragment(Fragment fragment, VideoGroup group) {
        if (fragment instanceof RowHeaderFragment) {
            RowHeaderFragment rowFragment = (RowHeaderFragment) fragment;
            rowFragment.updateRow(group);
        } else if (fragment instanceof GridHeaderFragment) {
            GridHeaderFragment gridFragment = (GridHeaderFragment) fragment;
            gridFragment.updateGrid(group);
        } else {
            throw new IllegalStateException("Page group fragment has incompatible type");
        }
    }

    private void addToPending(VideoGroup group, int headerId) {
        List<VideoGroup> videoGroups = mPendingUpdates.get(headerId);

        if (videoGroups == null) {
            videoGroups = new ArrayList<>();
            mPendingUpdates.put(headerId, videoGroups);
        }

        videoGroups.add(group);
    }

    private void updateFromPending(Fragment fragment, int headerId) {
        List<VideoGroup> videoGroups = mPendingUpdates.get(headerId);

        if (videoGroups != null) {
            for (VideoGroup group : videoGroups) {
                updateFragment(fragment, group);
            }
        }
    }

    public void clearFragment(int headerId) {
        mPendingUpdates.remove(headerId);
    }
}
