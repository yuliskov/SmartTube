package com.liskovsoft.smartyoutubetv2.common.mvp.views;

import com.liskovsoft.smartyoutubetv2.common.mvp.models.VideoGroup;

public interface SearchView {
    void updateSearch(VideoGroup group);
    void clearSearch();
}
