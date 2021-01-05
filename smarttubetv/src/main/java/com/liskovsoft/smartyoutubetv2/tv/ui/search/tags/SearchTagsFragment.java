package com.liskovsoft.smartyoutubetv2.tv.ui.search.tags;

import android.os.Bundle;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.leanback.widget.Presenter;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.MediaServiceSearchTagProvider;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.tv.adapter.VideoGroupObjectAdapter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.CardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.base.OnItemViewClickedListener;
import com.liskovsoft.smartyoutubetv2.tv.ui.search.tags.vineyard.SearchTagsFragmentBase;

public class SearchTagsFragment extends SearchTagsFragmentBase {
    private static final String TAG = SearchTagsFragment.class.getSimpleName();
    private SearchPresenter mSearchPresenter;
    private VideoGroupObjectAdapter mItemResultsAdapter;
    private String mSearchQuery;
    private String mNewQuery;
    private CardPresenter mCardPresenter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSearchPresenter = SearchPresenter.instance(getContext());
        mSearchPresenter.setView(this);
        mCardPresenter = new CardPresenter();
        mItemResultsAdapter = new VideoGroupObjectAdapter(mCardPresenter);

        setupEventListeners();
        setItemResultsAdapter(mItemResultsAdapter);
        setSearchTagsProvider(new MediaServiceSearchTagProvider());
    }

    private void setupEventListeners() {
        mCardPresenter.setOnLongClickedListener(new ItemViewLongClickedListener());
        mCardPresenter.setOnMenuPressedListener(new ItemViewLongClickedListener());
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mSearchPresenter.onViewInitialized();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSearchPresenter.onViewDestroyed();
    }

    @Override
    public void updateSearch(VideoGroup group) {
        freeze(true);
        mItemResultsAdapter.append(group);
        freeze(false);
    }

    @Override
    public void clearSearch() {
        mSearchQuery = null;
        mItemResultsAdapter.clear();
    }

    @Override
    public void startSearch(String searchText) {
        startSearch(searchText, false);
    }

    @Override
    public void startVoiceRecognition() {
        startSearch(null, true);
    }
    
    private void startSearch(String searchText, boolean enableRecognition) {
        mNewQuery = null;

        if (searchText != null) {
            setSearchQuery(searchText, true);
        } else {
            selectAllText();
            loadSearchTags("");

            if (enableRecognition) {
                startRecognition();
            } else {
                focusOnSearchField();
            }
        }

        if (getRowsSupportFragment() != null) {
            // Move selection to the top
            getRowsSupportFragment().setSelectedPosition(0);
        }
    }

    @Override
    public boolean onQueryTextChange(String newQuery) {
        loadSearchTags(newQuery);

        if (isVoiceQuery(newQuery)) {
            loadSearchResult(newQuery);
        }

        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        loadSearchResult(query);
        return true;
    }

    @Override
    protected void focusOnResults() {
        if (!TextUtils.isEmpty(mNewQuery)) {
            super.focusOnResults();
            if (getRowsSupportFragment() != null) {
                // Move selection to the top
                getRowsSupportFragment().setSelectedPosition(1);
            }
        }
    }

    private void loadSearchResult(String searchQuery) {
        if (!TextUtils.isEmpty(searchQuery) && !searchQuery.equals(mSearchQuery)) {
            mSearchQuery = searchQuery;
            mSearchPresenter.onSearch(searchQuery);
        }
    }

    private boolean isVoiceQuery(String newQuery) {
        if (TextUtils.isEmpty(newQuery)) {
            mNewQuery = null;
            return false;
        }

        boolean isVoice = mNewQuery == null && newQuery.length() > 1;

        mNewQuery = newQuery;

        return isVoice;
    }

    private final class ItemViewLongClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemViewClicked(Presenter.ViewHolder itemViewHolder, Object item) {
            if (item instanceof Video) {
                mSearchPresenter.onVideoItemLongClicked((Video) item);
            } else if (item instanceof Tag) {
                startSearch(((Tag) item).tag, false);
            }
        }
    }

    @Override
    protected void onItemViewClicked(Object item) {
        if (item instanceof Video) {
            mSearchPresenter.onVideoItemClicked((Video) item);
        } else if (item instanceof Tag) {
            startSearch(((Tag) item).tag, false);
        }
    }

    @Override
    protected void onItemViewSelected(Object item) {
        if (item instanceof Video) {
            checkScrollEnd((Video) item);
        }
    }

    private void checkScrollEnd(Video item) {
        int size = mItemResultsAdapter.size();
        int index = mItemResultsAdapter.indexOf(item);

        if (index > (size - 4)) {
            mSearchPresenter.onScrollEnd(mItemResultsAdapter.getGroup());
        }
    }
}
