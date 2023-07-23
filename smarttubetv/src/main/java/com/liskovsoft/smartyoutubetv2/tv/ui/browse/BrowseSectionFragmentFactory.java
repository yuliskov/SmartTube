package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import androidx.fragment.app.Fragment;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.Row;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.dialog.ErrorDialogFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces.CategoryFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces.SettingsCategoryFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces.VideoCategoryFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.settings.SettingsGridFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.video.MultiVideoGridFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.video.ShortsGridFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.video.VideoGridFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.video.VideoRowsFragment;

public class BrowseSectionFragmentFactory extends BrowseSupportFragment.FragmentFactory<Fragment> {
    private static final String TAG = BrowseSectionFragmentFactory.class.getSimpleName();
    private final OnSectionSelectedListener mSectionSelectedListener;
    private Fragment mCurrentFragment;
    private int mFragmentType = BrowseSection.TYPE_GRID;
    private int mSelectedItemIndex = -1;

    public interface OnSectionSelectedListener {
        void onSectionSelected(Row row);
    }

    public BrowseSectionFragmentFactory() {
        this(null);
    }

    public BrowseSectionFragmentFactory(OnSectionSelectedListener sectionSelectedListener) {
        mSectionSelectedListener = sectionSelectedListener;
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

        if (header instanceof SectionHeaderItem) {
            mFragmentType = ((SectionHeaderItem) header).getType();
        }

        Fragment fragment = null;

        switch (mFragmentType) {
            case BrowseSection.TYPE_ROW:
                fragment = new VideoRowsFragment();
                break;
            case BrowseSection.TYPE_GRID:
                fragment = new VideoGridFragment();
                break;
            case BrowseSection.TYPE_SHORTS_GRID:
                fragment = new ShortsGridFragment();
                break;
            case BrowseSection.TYPE_SETTINGS_GRID:
                fragment = new SettingsGridFragment();
                break;
            case BrowseSection.TYPE_MULTI_GRID:
                fragment = new MultiVideoGridFragment();
                break;
            case BrowseSection.TYPE_ERROR:
                fragment = new ErrorDialogFragment((ErrorFragmentData) ((SectionHeaderItem) header).getSection().getData());
                break;
        }

        if (fragment != null) {
            mCurrentFragment = fragment;

            // give a chance to clear pending updates
            if (mSectionSelectedListener != null) {
                mSectionSelectedListener.onSectionSelected(row);
            }
            
            setCurrentFragmentItemIndex(mSelectedItemIndex);

            return fragment;
        }

        throw new IllegalArgumentException(String.format("Invalid row %s", rowObj));
    }

    public void updateCurrentFragment(SettingsGroup group) {
        if (group == null) {
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
        if (group == null) {
            return;
        }

        if (mCurrentFragment == null) {
            Log.e(TAG, "Page row fragment not initialized for group: " + group.getTitle());
            return;
        }

        updateVideoFragment(mCurrentFragment, group);
    }

    public void clearCurrentFragment() {
        if (mCurrentFragment != null) {
            clearFragment(mCurrentFragment);
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

    public int getCurrentFragmentItemIndex() {
        if (mCurrentFragment instanceof VideoCategoryFragment) {
            return ((VideoCategoryFragment) mCurrentFragment).getPosition();
        }

        return -1;
    }

    public void setCurrentFragmentItemIndex(int index) {
        if (mCurrentFragment instanceof VideoCategoryFragment) {
            ((VideoCategoryFragment) mCurrentFragment).setPosition(index);
            mSelectedItemIndex = -1;
        } else {
            mSelectedItemIndex = index;
        }
    }

    private void updateVideoFragment(Fragment fragment, VideoGroup group) {
        if (fragment instanceof VideoCategoryFragment) {
            ((VideoCategoryFragment) fragment).update(group);
        } else {
            Log.e(TAG, "updateFragment: Page group fragment has incompatible type: " + fragment.getClass().getSimpleName());
        }
    }

    private void clearFragment(Fragment fragment) {
        if (fragment instanceof CategoryFragment) {
            ((CategoryFragment) fragment).clear();
        } else {
            Log.e(TAG, "clearFragment: Page group fragment has incompatible type: " + fragment.getClass().getSimpleName());
        }
    }
}
