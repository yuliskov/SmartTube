package com.liskovsoft.smartyoutubetv2.common.app.models.search;

import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;

import java.util.ArrayList;
import java.util.List;

public class PrefsSearchTagsProvider implements SearchTagsProvider {
    @Override
    public void search(String query, ResultsCallback callback) {
        List<Tag> tags = new ArrayList<>();
        tags.add(new Tag("One"));
        tags.add(new Tag("Two"));
        tags.add(new Tag("Three"));
        tags.add(new Tag("Four"));
        tags.add(new Tag("Five"));

        callback.onResults(tags);
    }
}
