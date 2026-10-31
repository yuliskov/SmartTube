package com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

public interface VideoSection extends Section {
    void update(VideoGroup group);
    int getPosition();
    void setPosition(int index);
    void selectItem(Video item);
}
