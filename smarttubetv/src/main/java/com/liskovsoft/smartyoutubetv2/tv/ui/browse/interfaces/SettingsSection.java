package com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;

public interface SettingsSection extends Section {
    void update(SettingsGroup items);
}
