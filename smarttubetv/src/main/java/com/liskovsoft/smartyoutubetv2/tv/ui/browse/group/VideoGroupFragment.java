package com.liskovsoft.smartyoutubetv2.tv.ui.browse.group;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

public interface VideoGroupFragment {
    void update(VideoGroup group);
    void invalidate();
    void clear();
    boolean isEmpty();
}
