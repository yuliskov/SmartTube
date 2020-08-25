package com.liskovsoft.smartyoutubetv2.tv.ui.main;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Toast;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.PageRow;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.Header;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.Video;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.mvp.presenters.MainPresenter;
import com.liskovsoft.smartyoutubetv2.common.mvp.views.MainView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.presenter.GridItemPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.IconHeaderItemPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.old.BrowseErrorFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.old.GuidedStepActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.old.SettingsActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.old.VerticalGridActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.old.VideoDetailsActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.onboarding.OnboardingActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.search.SearchActivity;

import java.util.HashMap;
import java.util.Map;

/*
 * Main class to show BrowseFragment with header and rows of videos
 */
public class MainFragment extends BrowseSupportFragment implements MainView {
    private static final int BACKGROUND_UPDATE_DELAY_MS = 300;
    private static final String TAG = MainFragment.class.getSimpleName();
    private final Handler mHandler = new Handler();
    private ArrayObjectAdapter mCategoryRowAdapter;
    private Drawable mDefaultBackground;
    private DisplayMetrics mMetrics;
    private Runnable mBackgroundTask;
    private Uri mBackgroundURI;
    private BackgroundManager mBackgroundManager;

    private MainPresenter mPresenter;
    private Map<Integer, Header> mHeaders;
    private PageRowFragmentFactory mPageRowFragmentFactory;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        
        mHeaders = new HashMap<>();
        mPresenter = MainPresenter.instance(context.getApplicationContext());
        mPresenter.subscribe(this);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        // Final initialization, modifying UI elements.
        super.onActivityCreated(savedInstanceState);

        // Prepare the manager that maintains the same background image between activities.
        prepareBackgroundManager();

        setupUIElements();
        setupEventListeners();
        prepareEntranceTransition();

        // Map category results from the database to ListRow objects.
        // This Adapter is used to render the MainFragment sidebar labels.
        mCategoryRowAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        setAdapter(mCategoryRowAdapter);

        mPageRowFragmentFactory = new PageRowFragmentFactory(mBackgroundManager);
        getMainFragmentRegistry().registerFragment(PageRow.class, mPageRowFragmentFactory);

        initRowAdapters();
    }

    @Override
    public void onDestroy() {
        mHandler.removeCallbacks(mBackgroundTask);
        mBackgroundManager = null;
        super.onDestroy();
    }

    @Override
    public void onStop() {
        mBackgroundManager.release();
        super.onStop();
    }

    private void prepareBackgroundManager() {
        mBackgroundManager = BackgroundManager.getInstance(getActivity());
        mBackgroundManager.attach(getActivity().getWindow());
        mDefaultBackground = ContextCompat.getDrawable(getActivity(), R.drawable.default_background);
        mBackgroundTask = new UpdateBackgroundTask();
        mMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
    }

    private void setupUIElements() {
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
        setOnSearchClickedListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getActivity(), SearchActivity.class);
                startActivity(intent);
            }
        });

        setOnItemViewClickedListener(new ItemViewClickedListener());
        setOnItemViewSelectedListener(new ItemViewSelectedListener());
    }

    private void updateBackground(String uri) {
        int width = mMetrics.widthPixels;
        int height = mMetrics.heightPixels;

        RequestOptions options = new RequestOptions()
                .centerCrop()
                .error(mDefaultBackground);

        Glide.with(this)
                .asBitmap()
                .load(uri)
                .apply(options)
                .into(new SimpleTarget<Bitmap>(width, height) {
                    @Override
                    public void onResourceReady(
                            Bitmap resource,
                            Transition<? super Bitmap> transition) {
                        mBackgroundManager.setBitmap(resource);
                    }
                });
    }

    private void startBackgroundTimer() {
        mHandler.removeCallbacks(mBackgroundTask);
        mHandler.postDelayed(mBackgroundTask, BACKGROUND_UPDATE_DELAY_MS);
    }

    private class UpdateBackgroundTask implements Runnable {

        @Override
        public void run() {
            if (mBackgroundURI != null) {
                updateBackground(mBackgroundURI.toString());
            }
        }
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                RowPresenter.ViewHolder rowViewHolder, Row row) {

            if (item instanceof Video) {
                Video video = (Video) item;
                Intent intent = new Intent(getActivity(), VideoDetailsActivity.class);
                intent.putExtra(VideoDetailsActivity.VIDEO, video);

                Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        getActivity(),
                        ((ImageCardView) itemViewHolder.view).getMainImageView(),
                        VideoDetailsActivity.SHARED_ELEMENT_NAME).toBundle();
                getActivity().startActivity(intent, bundle);
            } else if (item instanceof String) {
                if (((String) item).contains(getString(R.string.grid_view))) {
                    Intent intent = new Intent(getActivity(), VerticalGridActivity.class);
                    Bundle bundle =
                            ActivityOptionsCompat.makeSceneTransitionAnimation(getActivity())
                                    .toBundle();
                    startActivity(intent, bundle);
                } else if (((String) item).contains(getString(R.string.guidedstep_first_title))) {
                    Intent intent = new Intent(getActivity(), GuidedStepActivity.class);
                    Bundle bundle =
                            ActivityOptionsCompat.makeSceneTransitionAnimation(getActivity())
                                    .toBundle();
                    startActivity(intent, bundle);
                } else if (((String) item).contains(getString(R.string.error_fragment))) {
                    BrowseErrorFragment errorFragment = new BrowseErrorFragment();
                    getFragmentManager().beginTransaction().replace(R.id.main_frame, errorFragment)
                            .addToBackStack(null).commit();
                } else if(((String) item).contains(getString(R.string.personal_settings))) {
                    Intent intent = new Intent(getActivity(), SettingsActivity.class);
                    Bundle bundle =
                            ActivityOptionsCompat.makeSceneTransitionAnimation(getActivity())
                                    .toBundle();
                    startActivity(intent, bundle);
                } else {
                    Toast.makeText(getActivity(), ((String) item), Toast.LENGTH_SHORT)
                            .show();
                }
            }
        }
    }

    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                RowPresenter.ViewHolder rowViewHolder, Row row) {
            if (item instanceof Video) {
                mBackgroundURI = Uri.parse(((Video) item).bgImageUrl);
                startBackgroundTimer();
            }

        }
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
        mCategoryRowAdapter.add(new ListRow(gridHeader, gridRowAdapter));

        startEntranceTransition(); // TODO: Move startEntranceTransition to after all

        mPresenter.onInitDone();
    }

    @Override
    public void updateRow(VideoGroup group, Header header) {
        if (mHeaders.get(header.getId()) == null) {
            mHeaders.put(header.getId(), header);
            createMultiRowHeader(header);
        }

        mPageRowFragmentFactory.updateRow(group, header);
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
            Row row = (Row) rowObj;

            mBackgroundManager.setDrawable(null);

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
