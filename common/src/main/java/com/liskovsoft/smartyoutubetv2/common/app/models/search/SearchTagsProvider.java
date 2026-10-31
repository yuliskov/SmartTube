package com.liskovsoft.smartyoutubetv2.common.app.models.search;

import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;

import java.util.List;

public interface SearchTagsProvider {
    interface ResultsCallback {
        void onResults(List<Tag> results);
    }
    void search(String query, ResultsCallback callback);
}
