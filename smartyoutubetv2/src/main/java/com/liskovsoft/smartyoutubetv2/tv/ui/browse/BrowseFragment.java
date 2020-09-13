package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.app.HeadersSupportFragment.OnHeaderViewSelectedListener;
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
import androidx.leanback.widget.RowHeaderPresenter.ViewHolder;
import androidx.leanback.widget.RowPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.models.auth.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Header;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.presenter.GridItemPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.IconHeaderItemPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.old.BrowseErrorFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.UriBackgroundManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.old.GuidedStepActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.old.SettingsActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.old.VerticalGridActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.search.SearchActivity;

import java.util.HashMap;
import java.util.Map;

/*
 * Main class to show BrowseFragment with header and rows of videos
 */
public class BrowseFragment extends BrowseSupportFragment implements BrowseView {
    private static final String TAG = BrowseFragment.class.getSimpleName();
    private ArrayObjectAdapter mCategoryRowAdapter;
    private BrowsePresenter mBrowsePresenter;
    private Map<Integer, Header> mHeaders;
    private HeaderFragmentFactory mHeaderFragmentFactory;
    private Handler mHandler;
    private UriBackgroundManager mBackgroundManager;
    private boolean mAttachCalledBefore;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        mHeaders = new HashMap<>();
        mHandler = new Handler();
        mBrowsePresenter = BrowsePresenter.instance(context.getApplicationContext());
        mBrowsePresenter.register(this);

        mAttachCalledBefore = true;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        // Final initialization, modifying UI elements.
        super.onActivityCreated(savedInstanceState);

        mBackgroundManager = ((LeanbackActivity) getActivity()).getBackgroundManager();

        // Map category results from the database to ListRow objects.
        // This Adapter is used to render the MainFragment sidebar labels.
        mCategoryRowAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        setAdapter(mCategoryRowAdapter);

        mHeaderFragmentFactory = new HeaderFragmentFactory(mBackgroundManager.getBackgroundManager(), new HeaderViewSelectedListener());
        getMainFragmentRegistry().registerFragment(PageRow.class, mHeaderFragmentFactory);

        setupUi();
        // Prepare the manager that maintains the same background image between activities.
        //prepareBackgroundManager();
        setupEventListeners();
        prepareEntranceTransition();

        initRowAdapters();

        mHandler.postDelayed(this::initSampleRow, 3_000);

        mBrowsePresenter.onInitDone();
    }

    private void setupUi() {
        setBadgeDrawable(ContextCompat.getDrawable(getActivity(), R.mipmap.app_logo));
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
                return new IconHeaderItemPresenter(getHeaderResId(o));
            }
        });
    }

    private int getHeaderResId(Object o) {
        if (o instanceof PageRow) {
            return ((CustomHeaderItem) ((PageRow) o).getHeaderItem()).getResId();
        }

        return -1;
    }

    private void setupEventListeners() {
        setOnSearchClickedListener(view -> {
            Intent intent = new Intent(getActivity(), SearchActivity.class);
            startActivity(intent);
        });

        setOnItemViewClickedListener(new ItemViewClickedListener());
        setOnItemViewSelectedListener(new ItemViewSelectedListener());

        // Listener moved to PageRowFragmentFactory
        //getHeadersSupportFragment().setOnHeaderViewSelectedListener(new HeaderViewSelectedListener());
    }

    private void initRowAdapters() {
        // Every time we have to re-get the category loader, we must re-create the sidebar.
        mCategoryRowAdapter.clear();
    }

    private void initSampleRow() {
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
    }

    @Override
    public void updateHeaderIfEmpty(ErrorFragmentData data) {
        mHeaderFragmentFactory.updateFragmentIfEmpty(data);
    }

    @Override
    public void updateHeader(VideoGroup group) {
        Header header = group.getHeader();

        if (mHeaders.get(header.getId()) == null) {
            mHeaders.put(header.getId(), header);
            createHeader(header);
        }

        mHeaderFragmentFactory.updateFragment(group);
    }

    private void createHeader(Header header) {
        HeaderItem headerItem = new CustomHeaderItem(header.getId(), header.getTitle(), header.getType(), header.getResId());;

        PageRow pageRow = new PageRow(headerItem);
        mCategoryRowAdapter.add(pageRow);
    }

    @Override
    public void clearHeader(Header header) {
        mHeaderFragmentFactory.clearFragment(header.getId());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mBrowsePresenter.unregister(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!mAttachCalledBefore) {
            mBrowsePresenter.onViewResumed();
        }

        mAttachCalledBefore = false;
    }

    @Override
    public void showProgressBar(boolean show) {
        if (show) {
            getProgressBarManager().show();
        } else {
            getProgressBarManager().hide();
        }
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
                mBackgroundManager.setBackgroundFrom((Video) item);
            } else {
                mBackgroundManager.removeBackground();
            }
        }
    }

    public final class HeaderViewSelectedListener implements OnHeaderViewSelectedListener {
        @Override
        public void onHeaderSelected(ViewHolder viewHolder, Row row) {
            mBrowsePresenter.onHeaderFocused(row.getHeaderItem().getId());
        }
    }
}
