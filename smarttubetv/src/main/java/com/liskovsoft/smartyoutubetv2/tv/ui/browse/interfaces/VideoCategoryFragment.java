package com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

public interface VideoCategoryFragment extends CategoryFragment {
    void update(VideoGroup group);
}
