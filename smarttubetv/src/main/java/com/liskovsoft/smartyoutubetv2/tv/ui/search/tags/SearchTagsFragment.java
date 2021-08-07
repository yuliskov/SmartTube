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
import com.liskovsoft.smartyoutubetv2.tv.presenter.VideoCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.base.OnItemViewPressedListener;
import com.liskovsoft.smartyoutubetv2.tv.ui.search.tags.vineyard.SearchTagsFragmentBase;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class SearchTagsFragment extends SearchTagsFragmentBase {
    private static final String TAG = SearchTagsFragment.class.getSimpleName();
    private SearchPresenter mSearchPresenter;
    private VideoGroupObjectAdapter mItemResultsAdapter;
    private String mSearchQuery;
    private String mNewQuery;
    private VideoCardPresenter mCardPresenter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSearchPresenter = SearchPresenter.instance(getContext());
        mSearchPresenter.setView(this);
        mCardPresenter = new VideoCardPresenter();
        mItemResultsAdapter = new VideoGroupObjectAdapter(mCardPresenter);

        setupEventListeners();
        setItemResultsAdapter(mItemResultsAdapter);
        setSearchTagsProvider(new MediaServiceSearchTagProvider());
    }

    private void setupEventListeners() {
        mCardPresenter.setOnItemViewLongPressedListener(new ItemViewLongClickedListener());
        mCardPresenter.setOnItemViewMenuPressedListener(new ItemViewLongClickedListener());
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

        attachAdapter(1, mItemResultsAdapter);
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
    public String getSearchText() {
        return getSearchFiledText();
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

        // Commit on voice input.
        // Note, that voice detection is far from ideal and may results duplicate search loading.
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
        // Disable annoying focus on video results when clicking on tags etc.

        //if (!TextUtils.isEmpty(mNewQuery)) {
        //    super.focusOnResults();
        //    if (getRowsSupportFragment() != null) {
        //        // Move selection to the videos (second row)
        //        getRowsSupportFragment().setSelectedPosition(1);
        //    }
        //}
    }

    private void loadSearchResult(String searchQuery) {
        if (!TextUtils.isEmpty(searchQuery) && !searchQuery.equals(mSearchQuery)) {
            mSearchQuery = searchQuery;
            mSearchPresenter.onSearch(searchQuery);
        }
    }

    /**
     * Check that previous query is null
     */
    private boolean isVoiceQuery(String newQuery) {
        if (TextUtils.isEmpty(newQuery)) {
            mNewQuery = null;
            return false;
        }

        boolean isVoice = mNewQuery == null && newQuery.length() > 1;

        mNewQuery = newQuery;

        return isVoice;
    }

    private final class ItemViewLongClickedListener implements OnItemViewPressedListener {
        @Override
        public void onItemPressed(Presenter.ViewHolder itemViewHolder, Object item) {
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

        if (index > (size - ViewUtil.ROW_SCROLL_CONTINUE_NUM)) {
            mSearchPresenter.onScrollEnd((Video) mItemResultsAdapter.get(size - 1));
        }
    }
}
