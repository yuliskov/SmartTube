package com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;

public interface SettingsCategoryFragment extends CategoryFragment {
    void update(SettingsGroup items);
}
