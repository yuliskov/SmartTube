package com.liskovsoft.smartyoutubetv2.tv.ui.main;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.PageRow;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.Header;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.Video;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.mvp.presenters.MainPresenter;
import com.liskovsoft.smartyoutubetv2.common.mvp.views.MainView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.presenter.GridItemPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.IconHeaderItemPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.base.UriBackgroundManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.main.row.RowHeaderItem;
import com.liskovsoft.smartyoutubetv2.tv.ui.old.BrowseErrorFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.old.GuidedStepActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.old.SettingsActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.old.VerticalGridActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.old.VideoDetailsActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.onboarding.OnboardingActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;
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
    private UriBackgroundManager mBackgroundManager;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        
        mHeaders = new HashMap<>();
        mHandler = new Handler();
        mPresenter = MainPresenter.instance(context.getApplicationContext());
        mPresenter.register(this);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        // Final initialization, modifying UI elements.
        super.onActivityCreated(savedInstanceState);

        mBackgroundManager = UriBackgroundManager.instance(getActivity());

        // Map category results from the database to ListRow objects.
        // This Adapter is used to render the MainFragment sidebar labels.
        mCategoryRowAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        setAdapter(mCategoryRowAdapter);

        mPageRowFragmentFactory = new PageRowFragmentFactory(mBackgroundManager.getBackgroundManager());
        getMainFragmentRegistry().registerFragment(PageRow.class, mPageRowFragmentFactory);

        setupUi();
        // Prepare the manager that maintains the same background image between activities.
        //prepareBackgroundManager();
        setupEventListeners();
        prepareEntranceTransition();

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

        setOnItemViewClickedListener(new ItemViewClickedListener());
        setOnItemViewSelectedListener(new ItemViewSelectedListener());
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
    public void updateRowHeader(VideoGroup row, Header header) {
        if (mHeaders.get(header.getId()) == null) {
            mHeaders.put(header.getId(), header);
            createMultiRowHeader(header);
        }

        mPageRowFragmentFactory.updateRowFragment(row, header.getId());
    }

    private void createMultiRowHeader(Header header) {
        HeaderItem headerItem = new RowHeaderItem(header.getId(), header.getTitle());
        PageRow pageRow = new PageRow(headerItem);
        mCategoryRowAdapter.add(pageRow);
    }

    @Override
    public void showOnboarding() {
        startActivity(new Intent(getContext(), OnboardingActivity.class));
    }

    @Override
    public void updateGridHeader(VideoGroup grid, Header header) {
        // TODO: not implemented
    }

    @Override
    public void clearRowHeader(Header header) {
        // TODO: not implemented
    }

    @Override
    public void clearGridHeader(Header header) {
        // TODO: not implemented
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {

            if (item instanceof String) {
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
                Uri backgroundURI = Uri.parse(((Video) item).bgImageUrl);
                mBackgroundManager.startBackgroundTimer(backgroundURI);
            } else {
                mBackgroundManager.getBackgroundManager().setDrawable(null);
            }
        }
    }

    @Override
    public void openPlaybackView(Video item) {
        Intent intent = new Intent(getActivity(), PlaybackActivity.class);
        intent.putExtra(VideoDetailsActivity.VIDEO, item);
        startActivity(intent);
    }

    @Override
    public void openDetailsView(Video item) {
        Intent intent = new Intent(getActivity(), VideoDetailsActivity.class);
        intent.putExtra(VideoDetailsActivity.VIDEO, item);

        //Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
        //        getActivity(),
        //        ((ImageCardView) itemViewHolder.view).getMainImageView(),
        //        VideoDetailsActivity.SHARED_ELEMENT_NAME).toBundle();
        //getActivity().startActivity(intent, bundle);

        startActivity(intent);
    }
}
