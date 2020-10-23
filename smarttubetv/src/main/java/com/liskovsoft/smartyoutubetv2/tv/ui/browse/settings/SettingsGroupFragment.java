package com.liskovsoft.smartyoutubetv2.tv.ui.browse.settings;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;

public interface SettingsGroupFragment {
    void update(SettingsGroup items);
    void invalidate();
    void clear();
    boolean isEmpty();
}
