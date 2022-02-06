package com.liskovsoft.smartyoutubetv2.tv.ui.search.tags.vineyard;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.RowPresenter.ViewHolder;
import com.liskovsoft.sharedutils.helpers.PermissionHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.SearchTagsProvider;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.tv.adapter.vineyard.PaginationAdapter;
import com.liskovsoft.smartyoutubetv2.tv.adapter.vineyard.TagAdapter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.CustomListRowPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc.ProgressBarManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.search.SearchSupportFragment;

import java.util.ArrayList;
import java.util.List;

public abstract class SearchTagsFragmentBase extends SearchSupportFragment
        implements SearchSupportFragment.SearchResultProvider, SearchView {
    private static final String TAG = SearchTagsFragmentBase.class.getSimpleName();
    private static final int REQUEST_SPEECH = 0x00000010;

    private TagAdapter mSearchTagsAdapter;
    private ObjectAdapter mItemResultsAdapter;
    private ArrayObjectAdapter mResultsAdapter; // contains tags adapter and results adapter (see attachAdapter method)
    private ListRowPresenter mResultsPresenter;

    private boolean mIsStopping;
    private SearchTagsProvider mSearchTagsProvider;
    private ProgressBarManager mProgressBarManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mProgressBarManager = new ProgressBarManager();
        mResultsPresenter = new CustomListRowPresenter();
        mResultsAdapter = new ArrayObjectAdapter(mResultsPresenter);
        mSearchTagsAdapter = new TagAdapter(getActivity(), "");
        setSearchResultProvider(this);
        setupListeners();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = super.onCreateView(inflater, container, savedInstanceState);

        mProgressBarManager.setRootView((ViewGroup) root);

        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        mIsStopping = false;
    }

    @Override
    public void onStop() {
        super.onStop();
        mIsStopping = true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_SPEECH:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        setSearchQuery(data, true);
                        break;
                    case Activity.RESULT_CANCELED:
                        Log.i(TAG, "Recognizer canceled");
                        break;
                }
                break;
        }
    }

    @Override
    public ObjectAdapter getResultsAdapter() {
        return mResultsAdapter;
    }

    @Override
    public void showProgressBar(boolean show) {
        if (show) {
            mProgressBarManager.show();
        } else {
            mProgressBarManager.hide();
        }
    }

    protected abstract void onItemViewSelected(Object item);
    
    protected abstract void onItemViewClicked(Object item);

    protected void setItemResultsAdapter(ObjectAdapter adapter) {
        mItemResultsAdapter = adapter;
    }

    protected void setSearchTagsProvider(SearchTagsProvider provider) {
        mSearchTagsProvider = provider;
    }

    public boolean isStopping() {
        return mIsStopping;
    }

    public boolean hasResults() {
        return mResultsAdapter.size() > 0;
    }

    @SuppressWarnings("deprecation")
    private void setupListeners() {
        setOnItemViewClickedListener((itemViewHolder, item, rowViewHolder, row) -> onItemViewClicked(item));
        setOnItemViewSelectedListener((itemViewHolder, item, rowViewHolder, row) -> onItemViewSelected(item));

        // Use system speech recognition dialog instead of internal one.
        // More robust. Works in all cases but could lead to problems with dpi.
        // NOTE: to revert to internal dialog just comment out code below.
        if (!PermissionHelpers.hasPermission(getContext(), Manifest.permission.RECORD_AUDIO)) {
            setSpeechRecognitionCallback(() -> {
                if (isAdded()) {
                    try {
                        startActivityForResult(getRecognizerIntent(), REQUEST_SPEECH);
                    } catch (ActivityNotFoundException e) {
                        Log.e(TAG, "Cannot find activity for speech recognizer", e);
                    } catch (NullPointerException e) {
                        Log.e(TAG, "Speech recognizer can't obtain applicationInfo", e);
                    }
                } else {
                    Log.e(TAG, "Can't perform search. Fragment is detached.");
                }
            });
        }
    }

    protected void loadSearchTags(String searchQuery) {
        searchTaggedPosts(searchQuery);
    }

    private void searchTaggedPosts(String query) {
        mSearchTagsAdapter.setTag(query);
        mResultsAdapter.clear();
        mSearchTagsAdapter.clear();
        performTagSearch(mSearchTagsAdapter);
    }

    private void performTagSearch(PaginationAdapter adapter) {
        String query = adapter.getAdapterOptions().get(PaginationAdapter.KEY_TAG);
        mSearchTagsProvider.search(query, results -> {
            adapter.addAllItems(results);
            attachAdapter(0, adapter);
            // Same suggestions in the keyboard
            //displayCompletions(toCompletions(results));
        });
    }

    private List<String> toCompletions(List<Tag> results) {
        List<String> result = null;

        if (results != null) {
            result = new ArrayList<>();

            for (Tag tag : results) {
                result.add(tag.tag);
            }
        }

        return result;
    }

    /**
     * Disable scrolling on partially updated rows. This prevent controls from misbehaving.
     */
    protected void freeze(boolean freeze) {
        // Disable scrolling on partially updated rows. This prevent controls from misbehaving.
        RowsSupportFragment rowsSupportFragment = getRowsSupportFragment();
        if (mResultsPresenter != null && rowsSupportFragment != null) {
            ViewHolder vh = rowsSupportFragment.getRowViewHolder(rowsSupportFragment.getSelectedPosition());
            if (vh != null) {
                mResultsPresenter.freeze(vh, freeze);
            }
        }
    }

    protected void attachAdapter(int index, ObjectAdapter adapter) {
        if (mResultsAdapter != null) {
            if (!containsAdapter(adapter)) {
                index = Math.min(index, mResultsAdapter.size());
                mResultsAdapter.add(index, new ListRow(adapter));
            }
        }
    }

    protected void detachAdapter(int index) {
        if (mResultsAdapter != null && index < mResultsAdapter.size()) {
            mResultsAdapter.removeItems(index, 1);
        }
    }

    private boolean containsAdapter(ObjectAdapter adapter) {
        if (mResultsAdapter != null) {
            for (int i = 0; i < mResultsAdapter.size(); i++) {
                ListRow row = (ListRow) mResultsAdapter.get(i);
                if (row.getAdapter() == adapter) {
                    return true;
                }
            }
        }

        return false;
    }
}