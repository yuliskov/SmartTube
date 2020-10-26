package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import androidx.fragment.app.Fragment;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.app.HeadersSupportFragment.OnHeaderViewSelectedListener;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.Row;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Category;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces.CategoryFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces.SettingsCategoryFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces.VideoCategoryFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.settings.SettingsGridFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.video.VideoGridFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.video.VideoRowsFragment;

import java.util.ArrayList;
import java.util.List;

public class CategoryFragmentFactory extends BrowseSupportFragment.FragmentFactory<Fragment> {
    private static final String TAG = CategoryFragmentFactory.class.getSimpleName();
    private final OnHeaderViewSelectedListener mViewSelectedListener;
    private final List<VideoGroup> mCachedData;
    private Fragment mCurrentFragment;
    private int mDefaultFragmentType = Category.TYPE_GRID;

    public CategoryFragmentFactory() {
        this(null);
    }

    public CategoryFragmentFactory(OnHeaderViewSelectedListener viewSelectedListener) {
        mViewSelectedListener = viewSelectedListener;
        mCachedData = new ArrayList<>();
    }

    /**
     * Called each time when header is changed.<br/>
     * So, no need to clear state.
     */
    @Override
    public Fragment createFragment(Object rowObj) {
        Log.d(TAG, "Creating PageRow fragment");

        Row row = (Row) rowObj;

        HeaderItem header = row.getHeaderItem();
        Fragment fragment = null;

        if (header instanceof CategoryHeaderItem) {
            mDefaultFragmentType = ((CategoryHeaderItem) header).getType();
        }

        if (mDefaultFragmentType == Category.TYPE_ROW) {
            fragment = new VideoRowsFragment();
        } else if (mDefaultFragmentType == Category.TYPE_GRID) {
            fragment = new VideoGridFragment();
        } else if (mDefaultFragmentType == Category.TYPE_TEXT_GRID) {
            fragment = new SettingsGridFragment();
        }

        if (fragment != null) {
            mCurrentFragment = fragment;

            // give a chance to clear pending updates
            if (mViewSelectedListener != null) {
                mViewSelectedListener.onHeaderSelected(null, row);
            }

            updateVideoFragmentFromCache(fragment);

            return fragment;
        }

        throw new IllegalArgumentException(String.format("Invalid row %s", rowObj));
    }

    public void updateCurrentFragment(SettingsGroup group) {
        if (group == null || group.isEmpty()) {
            return;
        }

        if (mCurrentFragment == null) {
            Log.e(TAG, "Page row fragment not initialized for group: " + group.getTitle());
            return;
        }

        if (mCurrentFragment instanceof SettingsCategoryFragment) {
            ((SettingsCategoryFragment) mCurrentFragment).update(group);
        } else {
            Log.e(TAG, "updateFragment: Page group fragment has incompatible type: " + mCurrentFragment.getClass().getSimpleName());
        }
    }

    public void updateCurrentFragment(VideoGroup group) {
        if (group == null || group.isEmpty()) {
            return;
        }

        if (mCurrentFragment == null) {
            Log.e(TAG, "Page row fragment not initialized for group: " + group.getTitle());
            return;
        }

        mCachedData.add(group);

        updateVideoFragment(mCurrentFragment, group);
    }

    private void updateVideoFragment(Fragment fragment, VideoGroup group) {
        if (fragment instanceof VideoCategoryFragment) {
            ((VideoCategoryFragment) fragment).update(group);
        } else {
            Log.e(TAG, "updateFragment: Page group fragment has incompatible type: " + fragment.getClass().getSimpleName());
        }
    }

    private void updateVideoFragmentFromCache(Fragment fragment) {
        for (VideoGroup group : mCachedData) {
            updateVideoFragment(fragment, group);
        }
    }

    public void clearCurrentFragment() {
        mCachedData.clear();

        if (mCurrentFragment != null) {
            clearFragment(mCurrentFragment);
        }
    }

    private void clearFragment(Fragment fragment) {
        if (fragment instanceof CategoryFragment) {
            ((CategoryFragment) fragment).invalidate();
        } else {
            Log.e(TAG, "clearFragment: Page group fragment has incompatible type: " + fragment.getClass().getSimpleName());
        }
    }

    public boolean isEmpty() {
        if (mCurrentFragment instanceof CategoryFragment) {
            return ((CategoryFragment) mCurrentFragment).isEmpty();
        }

        return false;
    }

    public Fragment getCurrentFragment() {
        return mCurrentFragment;
    }

    public void setDefaultFragmentType(int type) {
        mDefaultFragmentType = type;
    }

    public int getDefaultFragmentType() {
        return mDefaultFragmentType;
    }
}
