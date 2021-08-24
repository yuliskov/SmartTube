package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import android.os.Bundle;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.PageRow;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;
import androidx.leanback.widget.TitleHelper;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Category;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.presenter.IconHeaderItemPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.dialog.ErrorDialogFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc.ProgressBarManager;

import java.util.LinkedHashMap;
import java.util.Map;

/*
 * Main class to show BrowseFragment with header and rows of videos
 */
public class BrowseFragment extends BrowseSupportFragment implements BrowseView {
    private static final String TAG = BrowseFragment.class.getSimpleName();
    private static final String SELECTED_HEADER_INDEX = "SelectedHeaderIndex";
    private static final String SELECTED_ITEM_INDEX = "SelectedItemIndex";
    private ArrayObjectAdapter mCategoryRowAdapter;
    private BrowsePresenter mBrowsePresenter;
    private Map<Integer, Category> mCategories;
    private CategoryFragmentFactory mCategoryFragmentFactory;
    private Handler mHandler;
    private ProgressBarManager mProgressBarManager;
    private boolean mIsFragmentCreated;
    private int mRestoredHeaderIndex = -1;
    private int mRestoredItemIndex = -1;
    private boolean mFocusOnChildFragment;
    private GestureDetector mGestureDetector;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(null);

        mRestoredHeaderIndex = savedInstanceState != null ? savedInstanceState.getInt(SELECTED_HEADER_INDEX, -1) : -1;
        mRestoredItemIndex = savedInstanceState != null ? savedInstanceState.getInt(SELECTED_ITEM_INDEX, -1) : -1;
        mIsFragmentCreated = true;

        mCategories = new LinkedHashMap<>();
        mHandler = new Handler();
        mBrowsePresenter = BrowsePresenter.instance(getContext());
        mBrowsePresenter.setView(this);
        mProgressBarManager = new ProgressBarManager();

        setupAdapter();
        setupFragmentFactory();
        setupUi();

        enableMainFragmentScaling(false);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // Store position in case activity is crashed
        outState.putInt(SELECTED_HEADER_INDEX, getSelectedPosition());
        // Not robust. Because tab content often changed after reloading.
        outState.putInt(SELECTED_ITEM_INDEX, mCategoryFragmentFactory.getCurrentFragmentItemIndex());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = super.onCreateView(inflater, container, savedInstanceState);

        mProgressBarManager.setRootView((ViewGroup) root);

        return root;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setupEventListeners();

        prepareEntranceTransition();

        mBrowsePresenter.onViewInitialized();

        // Restore state after crash
        selectCategory(mRestoredHeaderIndex);
        mRestoredHeaderIndex = -1;

        // Restore state after crash
        selectItem(mRestoredItemIndex);
        mRestoredItemIndex = -1;
    }

    private void setupEventListeners() {
        getHeadersSupportFragment().setOnHeaderClickedListener(
                (viewHolder, row) -> {
                    long headerId = row.getHeaderItem().getId();
                    int newPosition = indexOf(headerId);

                    if (getHeadersSupportFragment().getSelectedPosition() != newPosition) {
                        // touch screen support
                        getHeadersSupportFragment().setSelectedPosition(newPosition);
                    } else {
                        // update section when clicked or pressed
                        mBrowsePresenter.onCategoryFocused((int) headerId);
                        startHeadersTransition(false);
                    }
                }
        );

        setOnSearchClickedListener(view -> SearchPresenter.instance(getActivity()).startSearch(null));
    }

    private int indexOf(long headerId) {
        int index = 0;
        for (Integer id : mCategories.keySet()) {
            if (id == headerId) {
                return index;
            }
            index++;
        }

        return 0;
    }

    private void setupAdapter() {
        // Map category results from the database to ListRow objects.
        // This Adapter is used to render the MainFragment sidebar labels.
        mCategoryRowAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        setAdapter(mCategoryRowAdapter);
    }

    private void setupFragmentFactory() {
        mCategoryFragmentFactory = new CategoryFragmentFactory(
                (viewHolder, row) -> {
                    focusOnChildFragment();
                    mBrowsePresenter.onCategoryFocused(getSelectedHeaderId());
                }
        );

        getMainFragmentRegistry().registerFragment(PageRow.class, mCategoryFragmentFactory);
    }

    private void setupUi() {
        setTitle(getString(R.string.browse_title)); // Badge, when set, takes precedent over title
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);

        int brandColorRes = Helpers.getThemeAttr(getActivity(), R.attr.brandColor);
        int brandAccentColorRes = Helpers.getThemeAttr(getActivity(), R.attr.brandAccentColor);
        int appLogoRes = Helpers.getThemeAttr(getActivity(), R.attr.appLogo);

        // Top right corner logo
        setBadgeDrawable(ContextCompat.getDrawable(getActivity(), appLogoRes));

        // Set fastLane (or headers) background color
        setBrandColor(ContextCompat.getColor(getActivity(), brandColorRes));

        // Set search icon color.
        setSearchAffordanceColor(ContextCompat.getColor(getActivity(), brandAccentColorRes));

        setHeaderPresenterSelector(new PresenterSelector() {
            @Override
            public Presenter getPresenter(Object o) {
                return new IconHeaderItemPresenter(getHeaderResId(o), getIconUrl(o));
            }
        });
    }

    private int getHeaderResId(Object o) {
        if (o instanceof PageRow) {
            return ((CategoryHeaderItem) ((PageRow) o).getHeaderItem()).getResId();
        }

        return -1;
    }

    private String getIconUrl(Object o) {
        if (o instanceof PageRow) {
            return ((CategoryHeaderItem) ((PageRow) o).getHeaderItem()).getIconUrl();
        }

        return null;
    }

    private int getSelectedHeaderId() {
        if (getSelectedPosition() >= mCategoryRowAdapter.size()) {
            return -1;
        }

        return (int) ((PageRow) mCategoryRowAdapter.get(getSelectedPosition())).getHeaderItem().getId();
    }
    
    public void updateErrorIfEmpty(ErrorFragmentData data) {
        mHandler.postDelayed(() -> showErrorIfEmpty(data), 500); // need delay because header may be not updated
    }

    @Override
    public void showError(ErrorFragmentData data) {
        //replaceMainFragment(new ErrorDialogFragment(data));
        showErrorIfEmpty(data);
    }

    private void showErrorIfEmpty(ErrorFragmentData data) {
        if (mCategoryFragmentFactory.isEmpty()) {
            replaceMainFragment(new ErrorDialogFragment(data));
        }
    }

    private void replaceMainFragment(Fragment fragment) {
        Object currentFragment = Helpers.getField(this,"mMainFragment");

        if (currentFragment != null && fragment != null && currentFragment != fragment) {
            Helpers.setField(this, "mMainFragment", fragment);

            FragmentTransaction ft = getChildFragmentManager().beginTransaction();
            ft.replace(R.id.scale_frame, fragment);
            ft.commitAllowingStateLoss(); // FIX: "Can not perform this action after onSaveInstanceState"
        }
    }

    @Override
    public void addCategory(int index, Category category) {
        if (category == null) {
            return;
        }

        if (mCategories.get(category.getId()) == null) {
            mCategories.put(category.getId(), category);
            createHeader(index, category);
        }
    }

    @Override
    public void removeCategory(Category category) {
        if (category == null) {
            return;
        }

        mCategories.remove(category.getId());
        removeHeader(category);
    }

    @Override
    public void updateCategory(VideoGroup group) {
        restoreMainFragment();

        mCategoryFragmentFactory.updateCurrentFragment(group);

        fixInvisibleSearchOrb();
    }

    @Override
    public void updateCategory(SettingsGroup group) {
        restoreMainFragment();

        mCategoryFragmentFactory.updateCurrentFragment(group);
    }

    @Override
    public void selectCategory(int index) {
        if (index >= 0 && index < mCategoryRowAdapter.size()) {
            setSelectedPosition(index);
            mFocusOnChildFragment = true;
        }
    }

    private void focusOnChildFragment() {
        if (!Utils.checkActivity(getActivity())) {
            return;
        }

        if (mFocusOnChildFragment) {
            startHeadersTransition(false);
            mFocusOnChildFragment = false;
        }
    }

    @Override
    public void selectItem(int index) {
        if (index >= 0) {
            mCategoryFragmentFactory.setCurrentFragmentItemIndex(index);
        }
    }

    private void restoreMainFragment() {
        Fragment currentFragment = mCategoryFragmentFactory.getCurrentFragment();

        if (currentFragment != null) {
            replaceMainFragment(currentFragment);
        }
    }

    private void createHeader(int index, Category header) {
        HeaderItem headerItem = new CategoryHeaderItem(header);

        PageRow pageRow = new PageRow(headerItem);
        if (index == -1 || mCategoryRowAdapter.size() < index) {
            mCategoryRowAdapter.add(pageRow); // add to the end
        } else {
            mCategoryRowAdapter.add(index, pageRow);
        }
    }

    private void removeHeader(Category header) {
        Object foundHeader = null;

        for (Object item : mCategoryRowAdapter.unmodifiableList()) {
            if (((PageRow) item).getHeaderItem().getId() == header.getId()) {
                foundHeader = item;
                break;
            }
        }

        if (foundHeader != null) {
            mCategoryRowAdapter.remove(foundHeader);
        }
    }

    @Override
    public void clearCategory(Category category) {
        mCategoryFragmentFactory.clearCurrentFragment();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mBrowsePresenter.onViewDestroyed();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!mIsFragmentCreated) {
            mBrowsePresenter.onViewResumed();
        }

        mIsFragmentCreated = false;
    }

    /**
     * Fix suddenly invisible search orb<br/>
     * Could happen on topmost category when the page partially scrolled<br/>
     * More info: {@link TitleHelper}
     */
    private void fixInvisibleSearchOrb() {
        if (isShowingTitle() && getTitleView() != null && getTitleView().getVisibility() != View.VISIBLE) {
            getTitleView().setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void showProgressBar(boolean show) {
        if (show) {
            mProgressBarManager.show();
        } else {
            mProgressBarManager.hide();
        }
    }

    @Override
    public boolean isProgressBarShowing() {
        return mProgressBarManager.isShowing();
    }
}
