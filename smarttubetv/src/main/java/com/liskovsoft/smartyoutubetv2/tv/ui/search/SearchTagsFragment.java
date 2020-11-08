package com.liskovsoft.smartyoutubetv2.tv.ui.search;

import android.os.Bundle;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.tv.adapter.VideoGroupObjectAdapter;
import com.liskovsoft.smartyoutubetv2.tv.ui.search.vineyard.SearchTagsFragmentBase;

public class SearchTagsFragment extends SearchTagsFragmentBase {
    private SearchPresenter mSearchPresenter;
    private VideoGroupObjectAdapter mItemResultsAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSearchPresenter = SearchPresenter.instance(getContext());
        mSearchPresenter.register(this);
        mItemResultsAdapter = new VideoGroupObjectAdapter();
        setItemResultsAdapter(mItemResultsAdapter);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mSearchPresenter.onInitDone();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSearchPresenter.unregister(this);
    }

    @Override
    public void updateSearch(VideoGroup group) {
        mItemResultsAdapter.append(group);
    }

    @Override
    public void clearSearch() {
        super.clearSearch();
        mItemResultsAdapter.clear();
    }

    @Override
    public void startSearch(String searchText) {
        if (searchText != null) {
            setSearchQuery(searchText, true);
        } else {
            startRecognition();
        }
    }

    @Override
    protected void loadQuery(String query) {
        super.loadQuery(query);

        if (!TextUtils.isEmpty(query) && !query.equals("nil")) {
            mSearchPresenter.onSearchText(query);
        }
    }
}
