package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
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
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Header;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.presenter.IconHeaderItemPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.dialog.LoginDialogFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.ProgressBarManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.search.SearchActivity;

import java.util.LinkedHashMap;
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
    private boolean mCreateAlreadyCalled;
    private ProgressBarManager mProgressBarManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHeaders = new LinkedHashMap<>();
        mHandler = new Handler();
        mBrowsePresenter = BrowsePresenter.instance(getContext());
        mBrowsePresenter.register(this);
        mCreateAlreadyCalled = true;
        mProgressBarManager = new ProgressBarManager();

        setupAdapter();
        setupUi();

        enableMainFragmentScaling(false);
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

        mBrowsePresenter.onInitDone();
    }

    private void setupAdapter() {
        // Map category results from the database to ListRow objects.
        // This Adapter is used to render the MainFragment sidebar labels.
        mCategoryRowAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        setAdapter(mCategoryRowAdapter);

        mHeaderFragmentFactory = new HeaderFragmentFactory(
                (viewHolder, row) -> mBrowsePresenter.onHeaderFocused(row.getHeaderItem().getId()));
        getMainFragmentRegistry().registerFragment(PageRow.class, mHeaderFragmentFactory);
    }

    private void setupUi() {
        setBadgeDrawable(ContextCompat.getDrawable(getActivity(), R.mipmap.app_logo));
        setTitle(getString(R.string.browse_title)); // Badge, when set, takes precedent over title
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);

        // Set fastLane (or headers) background color
        setBrandColor(ContextCompat.getColor(getActivity(), R.color.fastlane_background_dark));

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
            return ((FragmentHeaderItem) ((PageRow) o).getHeaderItem()).getResId();
        }

        return -1;
    }

    private void setupEventListeners() {
        getHeadersSupportFragment().setOnHeaderClickedListener(
                (viewHolder, row) -> {
                    mBrowsePresenter.onHeaderFocused(row.getHeaderItem().getId());
                });

        setOnSearchClickedListener(view -> {
            Intent intent = new Intent(getActivity(), SearchActivity.class);
            startActivity(intent);
        });
    }

    @Override
    public void updateErrorIfEmpty(ErrorFragmentData data) {
        mHandler.postDelayed(() -> showErrorIfEmpty(data), 500); // need delay because header may be not updated
    }

    private void showErrorIfEmpty(ErrorFragmentData data) {
        if (mHeaderFragmentFactory.isEmpty()) {
            replaceMainFragment(new LoginDialogFragment(data));
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
    public void updateHeader(VideoGroup group) {
        Header header = group.getHeader();

        if (mHeaders.get(header.getId()) == null) {
            mHeaders.put(header.getId(), header);
            createHeader(header);
        }

        restoreMainFragment();

        mHeaderFragmentFactory.updateFragment(group);
    }

    private void restoreMainFragment() {
        Fragment currentFragment = mHeaderFragmentFactory.getCurrentFragment();

        if (currentFragment != null) {
            replaceMainFragment(currentFragment);
        }
    }

    private void createHeader(Header header) {
        HeaderItem headerItem = new FragmentHeaderItem(header.getId(), header.getTitle(), header.getType(), header.getResId());;

        PageRow pageRow = new PageRow(headerItem);
        mCategoryRowAdapter.add(pageRow);
    }

    @Override
    public void clearHeader(Header header) {
        mHeaderFragmentFactory.clearFragment();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mBrowsePresenter.unregister(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!mCreateAlreadyCalled) {
            mBrowsePresenter.onViewResumed();
        }

        mCreateAlreadyCalled = false;
    }

    @Override
    public void showProgressBar(boolean show) {
        if (show) {
            mProgressBarManager.show();
        } else {
            mProgressBarManager.hide();
        }
    }
}
