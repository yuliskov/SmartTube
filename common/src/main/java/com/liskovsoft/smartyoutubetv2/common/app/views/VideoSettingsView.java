package com.liskovsoft.smartyoutubetv2.common.app.views;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.OptionItem;

import java.util.List;

public interface VideoSettingsView {
    void addCategory(String title, List<OptionItem> items);
}
