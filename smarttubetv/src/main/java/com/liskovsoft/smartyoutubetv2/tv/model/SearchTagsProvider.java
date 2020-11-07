package com.liskovsoft.smartyoutubetv2.tv.model;

import com.liskovsoft.smartyoutubetv2.tv.model.vineyard.Tag;

import java.util.List;

public interface SearchTagsProvider {
    interface ResultsCallback {
        void onResults(List<Tag> results);
    }
    void search(String query, ResultsCallback callback);
}
