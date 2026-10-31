package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import androidx.fragment.app.Fragment;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.Row;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.dialog.ErrorDialogFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces.Section;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces.SettingsSection;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces.VideoSection;
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
    private Video mSelectedItem;
    private Runnable mOnSectionSelected;

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

            runListeners(row);

            setCurrentFragmentItemIndex(mSelectedItemIndex);
            selectCurrentFragmentItem(mSelectedItem);

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

        if (mCurrentFragment instanceof SettingsSection) {
            ((SettingsSection) mCurrentFragment).update(group);
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
        if (mCurrentFragment instanceof Section) {
            return ((Section) mCurrentFragment).isEmpty();
        }

        return false;
    }

    public Fragment getCurrentFragment() {
        return mCurrentFragment;
    }

    public void cleanup() {
        Utils.removeCallbacks(mOnSectionSelected);
        mCurrentFragment = null;
        mOnSectionSelected = null;
    }

    public int getCurrentFragmentItemIndex() {
        if (mCurrentFragment instanceof VideoSection) {
            return ((VideoSection) mCurrentFragment).getPosition();
        }

        return -1;
    }

    public void setCurrentFragmentItemIndex(int index) {
        if (index < 0) {
            return;
        }

        mSelectedItemIndex = index;
        mSelectedItem = null;

        if (mCurrentFragment instanceof VideoSection) {
            ((VideoSection) mCurrentFragment).setPosition(index);
            mSelectedItemIndex = -1;
        }
    }

    public void selectCurrentFragmentItem(Video item) {
        if (item == null) {
            return;
        }

        mSelectedItem = item;
        mSelectedItemIndex = -1;

        if (mCurrentFragment instanceof VideoSection) {
            ((VideoSection) mCurrentFragment).selectItem(item);
            mSelectedItem = null;
        }
    }

    private void updateVideoFragment(Fragment fragment, VideoGroup group) {
        if (fragment instanceof VideoSection) {
            ((VideoSection) fragment).update(group);
        } else {
            Log.e(TAG, "updateFragment: Page group fragment has incompatible type: " + fragment.getClass().getSimpleName());
        }
    }

    private void clearFragment(Fragment fragment) {
        if (fragment instanceof Section) {
            ((Section) fragment).clear();
        } else {
            Log.e(TAG, "clearFragment: Page group fragment has incompatible type: " + fragment.getClass().getSimpleName());
        }
    }

    private void runListeners(Row row) {
        Utils.removeCallbacks(mOnSectionSelected);

        // give a chance to clear pending updates
        mOnSectionSelected = () -> {
            if (mSectionSelectedListener != null) {
                mSectionSelectedListener.onSectionSelected(row);
            }
        };

        // Wait till the main fragment changed
        Utils.postDelayed(mOnSectionSelected, 100);
    }
}
