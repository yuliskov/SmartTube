package com.liskovsoft.smartyoutubetv2.tv.ui.main;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.PageRow;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;
import androidx.leanback.widget.Row;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.Header;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.mvp.presenters.MainPresenter;
import com.liskovsoft.smartyoutubetv2.common.mvp.views.MainView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.presenter.GridItemPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.IconHeaderItemPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.onboarding.OnboardingActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.search.SearchActivity;

import java.util.HashMap;
import java.util.Map;

/*
 * Main class to show BrowseFragment with header and rows of videos
 */
public class MainFragment extends BrowseSupportFragment implements MainView {
    private static final String TAG = MainFragment.class.getSimpleName();
    private ArrayObjectAdapter mCategoryRowAdapter;
    private MainPresenter mPresenter;
    private Map<Integer, Header> mHeaders;
    private PageRowFragmentFactory mPageRowFragmentFactory;
    private Handler mHandler;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        
        mHeaders = new HashMap<>();
        mHandler = new Handler();
        mPresenter = MainPresenter.instance(context.getApplicationContext());
        mPresenter.subscribe(this);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        // Final initialization, modifying UI elements.
        super.onActivityCreated(savedInstanceState);

        setupUi();
        setupEventListeners();
        prepareEntranceTransition();

        // Map category results from the database to ListRow objects.
        // This Adapter is used to render the MainFragment sidebar labels.
        mCategoryRowAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        setAdapter(mCategoryRowAdapter);

        mPageRowFragmentFactory = new PageRowFragmentFactory(null);
        getMainFragmentRegistry().registerFragment(PageRow.class, mPageRowFragmentFactory);

        initRowAdapters();
    }

    private void setupUi() {
        setBadgeDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.videos_by_google_banner));
        setTitle(getString(R.string.browse_title)); // Badge, when set, takes precedent over title
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);

        // Set fastLane (or headers) background color
        setBrandColor(ContextCompat.getColor(getActivity(), R.color.fastlane_background));

        // Set search icon color.
        setSearchAffordanceColor(ContextCompat.getColor(getActivity(), R.color.search_opaque));

        setHeaderPresenterSelector(new PresenterSelector() {
            @Override
            public Presenter getPresenter(Object o) {
                return new IconHeaderItemPresenter();
            }
        });
    }

    private void setupEventListeners() {
        setOnSearchClickedListener(view -> {
            Intent intent = new Intent(getActivity(), SearchActivity.class);
            startActivity(intent);
        });
    }

    private void initRowAdapters() {
        // Every time we have to re-get the category loader, we must re-create the sidebar.
        mCategoryRowAdapter.clear();

        // Create a row for this special case with more samples.
        HeaderItem gridHeader = new HeaderItem(getString(R.string.more_samples));
        GridItemPresenter gridPresenter = new GridItemPresenter(this);
        ArrayObjectAdapter gridRowAdapter = new ArrayObjectAdapter(gridPresenter);
        gridRowAdapter.add(getString(R.string.grid_view));
        gridRowAdapter.add(getString(R.string.guidedstep_first_title));
        gridRowAdapter.add(getString(R.string.error_fragment));
        gridRowAdapter.add(getString(R.string.personal_settings));
        //mCategoryRowAdapter.add(new ListRow(gridHeader, gridRowAdapter));

        startEntranceTransition(); // TODO: Move startEntranceTransition to after all

        mPresenter.onInitDone();
    }

    @Override
    public void updateRow(VideoGroup group, Header header) {
        if (mHeaders.get(header.getId()) == null) {
            mHeaders.put(header.getId(), header);
            createMultiRowHeader(header);
        }

        mHandler.postDelayed(() -> mPageRowFragmentFactory.updateRow(group, header), 2_000);
    }

    private void createMultiRowHeader(Header header) {
        HeaderItem headerItem = new MultiRowHeaderItem(header.getId(), header.getTitle());
        PageRow pageRow = new PageRow(headerItem);
        mCategoryRowAdapter.add(pageRow);
    }

    @Override
    public void showOnboarding() {
        startActivity(new Intent(getContext(), OnboardingActivity.class));
    }

    @Override
    public void updateGrid(VideoGroup group, Header header) {
        // TODO: not implemented
    }

    @Override
    public void clearRow(Header header) {
        // TODO: not implemented
    }

    @Override
    public void clearGrid(Header header) {
        // TODO: not implemented
    }

    private static class PageRowFragmentFactory extends BrowseSupportFragment.FragmentFactory<Fragment> {
        private final BackgroundManager mBackgroundManager;
        private final Map<Integer, Fragment> mFragments;

        PageRowFragmentFactory(BackgroundManager backgroundManager) {
            mBackgroundManager = backgroundManager;
            mFragments = new HashMap<>();
        }

        @Override
        public Fragment createFragment(Object rowObj) {
            Log.d(TAG, "Creating PageRow fragment");

            Row row = (Row) rowObj;

            if (mBackgroundManager != null) {
                mBackgroundManager.setDrawable(null);
            }

            HeaderItem headerItem = row.getHeaderItem();
            Fragment fragment = null;

            if (headerItem instanceof MultiRowHeaderItem) {
                fragment = new MultiRowFragment();
            } else if (headerItem instanceof GridHeaderItem) {
                fragment = new GridFragment();
            }

            if (fragment != null) {
                mFragments.put((int) headerItem.getId(), fragment);
                return fragment;
            }

            throw new IllegalArgumentException(String.format("Invalid row %s", rowObj));
        }

        public void updateRow(VideoGroup group, Header header) {
            Fragment fragment = mFragments.get(header.getId());

            if (fragment == null) {
                throw new IllegalStateException("Page row fragment not initialized");
            }

            if (fragment instanceof MultiRowFragment) {
                MultiRowFragment rowFragment = (MultiRowFragment) fragment;
                rowFragment.updateRow(group);
            } else {
                throw new IllegalStateException("Page row fragment has incompatible type");
            }
        }
    }

    private static class MultiRowHeaderItem extends HeaderItem {
        public MultiRowHeaderItem(long id, String name) {
            super(id, name);
        }

        public MultiRowHeaderItem(String name) {
            super(name);
        }
    }

    private static class GridHeaderItem extends HeaderItem {
        public GridHeaderItem(long id, String name) {
            super(id, name);
        }

        public GridHeaderItem(String name) {
            super(name);
        }
    }
}
