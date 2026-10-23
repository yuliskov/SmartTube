package com.liskovsoft.smartyoutubetv2.tv.ui.modern.browse;

import android.view.View;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

/**
 * Content of the selected section (rows or grid).
 */
public interface ModernSection {
    View getView();
    void update(VideoGroup group);
    void clear();
    boolean isEmpty();
    int getPosition();
    void setPosition(int index);
    void selectItem(Video item);
    boolean focusContent();

    /**
     * A section that shows the loading state itself (e.g. only over a part of the page).
     */
    interface LoadingHost {
        void showLoading(boolean show);
    }
}
