package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import androidx.fragment.app.Fragment;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.Row;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Header;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.BrowseFragment.HeaderViewSelectedListener;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.grid.HeaderGridFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.row.HeaderRowFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HeaderFragmentFactory extends BrowseSupportFragment.FragmentFactory<Fragment> {
    private static final String TAG = HeaderFragmentFactory.class.getSimpleName();
    private final BackgroundManager mBackgroundManager;
    private final Map<Integer, Fragment> mFragments;
    private final Map<Integer, List<VideoGroup>> mPendingUpdates;
    private final HeaderViewSelectedListener mViewSelectedListener;

    public HeaderFragmentFactory(BackgroundManager backgroundManager) {
        this(backgroundManager, null);
    }

    public HeaderFragmentFactory(BackgroundManager backgroundManager, HeaderViewSelectedListener viewSelectedListener) {
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

        if (header instanceof CustomHeaderItem) {
            int type = ((CustomHeaderItem) header).getType();

            if (type == Header.TYPE_ROW) {
                fragment = new HeaderRowFragment();
            } else if (type == Header.TYPE_GRID) {
                fragment = new HeaderGridFragment();
            }
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

        addToPending(group, headerId);

        Fragment fragment = mFragments.get(headerId);

        if (fragment == null) {
            Log.e(TAG, "Page row fragment not initialized for group: " + group.getTitle());

            return;
        }

        updateFragment(fragment, group);
    }

    private void updateFragment(Fragment fragment, VideoGroup group) {
        if (fragment instanceof HeaderRowFragment) {
            HeaderRowFragment rowFragment = (HeaderRowFragment) fragment;
            rowFragment.updateRow(group);
        } else if (fragment instanceof HeaderGridFragment) {
            HeaderGridFragment gridFragment = (HeaderGridFragment) fragment;
            gridFragment.updateGrid(group);
        } else {
            Log.e(TAG, "updateFragment: Page group fragment has incompatible type: " + fragment.getClass().getSimpleName());
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

        Fragment fragment = mFragments.get(headerId);

        if (fragment != null) {
            clearFragment(fragment);
        }
    }

    private void clearFragment(Fragment fragment) {
        if (fragment instanceof HeaderRowFragment) {
            HeaderRowFragment rowFragment = (HeaderRowFragment) fragment;
            rowFragment.clear();
        } else if (fragment instanceof HeaderGridFragment) {
            HeaderGridFragment gridFragment = (HeaderGridFragment) fragment;
            gridFragment.clear();
        } else {
            Log.e(TAG, "clearFragment: Page group fragment has incompatible type: " + fragment.getClass().getSimpleName());
        }
    }
}
