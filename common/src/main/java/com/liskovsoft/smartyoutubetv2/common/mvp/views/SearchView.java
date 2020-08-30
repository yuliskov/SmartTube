package com.liskovsoft.smartyoutubetv2.common.mvp.views;

import com.liskovsoft.smartyoutubetv2.common.mvp.models.Video;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.VideoGroup;

public interface SearchView {
    void loadSearchResult(VideoGroup group);
    void openDetailsView(Video item);
}
