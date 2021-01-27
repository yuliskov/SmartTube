package com.liskovsoft.smartyoutubetv2.common.app.presenters.base;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AboutPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AccountSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AutoFrameRateSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.BlockSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.DeviceLinkSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.LanguageSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.MainUISettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.PlayerSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.SearchSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.SubtitleSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.UIScaleSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem.VideoPreset;

import java.util.ArrayList;
import java.util.List;

public class DataSourcePresenter extends BasePresenter<Void> {
    private static DataSourcePresenter sInstance;

    public DataSourcePresenter(Context context) {
        super(context);
    }

    public static DataSourcePresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new DataSourcePresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public List<SettingsItem> getSettingItems() {
        List<SettingsItem> settingItems = new ArrayList<>();

        settingItems.add(new SettingsItem(
                getContext().getString(R.string.settings_accounts), () -> AccountSettingsPresenter.instance(getContext()).show(), R.drawable.settings_account));
        settingItems.add(new SettingsItem(
                getContext().getString(R.string.settings_linked_devices), () -> DeviceLinkSettingsPresenter.instance(getContext()).show(), R.drawable.settings_cast));
        settingItems.add(new SettingsItem(
                getContext().getString(R.string.settings_language), () -> LanguageSettingsPresenter.instance(getContext()).show(), R.drawable.settings_language));
        settingItems.add(new SettingsItem(
                getContext().getString(R.string.settings_main_ui), () -> MainUISettingsPresenter.instance(getContext()).show(), R.drawable.settings_main_ui));
        settingItems.add(new SettingsItem(
                getContext().getString(R.string.settings_ui_scale), () -> UIScaleSettingsPresenter.instance(getContext()).show(), R.drawable.settings_ui_scale));
        settingItems.add(new SettingsItem(
                getContext().getString(R.string.settings_player), () -> PlayerSettingsPresenter.instance(getContext()).show(), R.drawable.settings_player));
        settingItems.add(new SettingsItem(
                getContext().getString(R.string.auto_frame_rate), () -> AutoFrameRateSettingsPresenter.instance(getContext()).show(), R.drawable.settings_afr));
        settingItems.add(new SettingsItem(
                getContext().getString(R.string.subtitle_category_title), () -> SubtitleSettingsPresenter.instance(getContext()).show(), R.drawable.settings_subtitles));
        settingItems.add(new SettingsItem(
                getContext().getString(R.string.settings_search), () -> SearchSettingsPresenter.instance(getContext()).show(), R.drawable.settings_search));
        settingItems.add(new SettingsItem(
                getContext().getString(R.string.settings_block), () -> BlockSettingsPresenter.instance(getContext()).show(), R.drawable.settings_block));
        settingItems.add(new SettingsItem(
                getContext().getString(R.string.settings_about), () -> AboutPresenter.instance(getContext()).show(), R.drawable.settings_about));

        return settingItems;
    }

    public VideoPreset[] getVideoPresets() {
        VideoPreset[] presets = {
                new VideoPreset("SD     30fps    avc", "640,360,30,avc"),
                new VideoPreset("SD     30fps    vp9", "640,360,30,vp9"),
                new VideoPreset("SD     60fps    avc", "640,360,60,avc"),
                new VideoPreset("SD     60fps    vp9", "640,360,60,vp9"),
                new VideoPreset("HD     30fps    avc", "1280,720,30,avc"),
                new VideoPreset("HD     30fps    vp9", "1280,720,30,vp9"),
                new VideoPreset("HD     60fps    avc", "1280,720,60,avc"),
                new VideoPreset("HD     60fps    vp9", "1280,720,60,vp9"),
                new VideoPreset("FHD    30fps    avc", "1920,1080,30,avc"),
                new VideoPreset("FHD    30fps    vp9", "1920,1080,30,vp9"),
                new VideoPreset("FHD    30fps    vp9+hdr", "1920,1080,30,vp9.2"),
                new VideoPreset("FHD    60fps    avc", "1920,1080,60,avc"),
                new VideoPreset("FHD    60fps    vp9", "1920,1080,60,vp9"),
                new VideoPreset("FHD    60fps    vp9+hdr", "1920,1080,60,vp9.2"),
                new VideoPreset("2K     30fps    vp9", "2560,1440,30,vp9"),
                new VideoPreset("2K     30fps    vp9+hdr", "2560,1440,30,vp9.2"),
                new VideoPreset("2K     60fps    vp9", "2560,1440,60,vp9"),
                new VideoPreset("2K     60fps    vp9+hdr", "2560,1440,60,vp9.2"),
                new VideoPreset("4K     30fps    vp9", "3840,2160,30,vp9"),
                new VideoPreset("4K     30fps    vp9+hdr", "3840,2160,30,vp9.2"),
                new VideoPreset("4K     60fps    vp9", "3840,2160,60,vp9"),
                new VideoPreset("4K     60fps    vp9+hdr", "3840,2160,60,vp9.2"),
                new VideoPreset("8K     30fps    vp9", "7680,4320,30,vp9"),
                new VideoPreset("8K     30fps    vp9+hdr", "7680,4320,30,vp9.2"),
                new VideoPreset("8K     60fps    vp9", "7680,4320,60,vp9"),
                new VideoPreset("8K     60fps    vp9+hdr", "7680,4320,60,vp9.2")
        };

        return presets;
    }
}
