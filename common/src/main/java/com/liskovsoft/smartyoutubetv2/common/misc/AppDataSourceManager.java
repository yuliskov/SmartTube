package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AboutSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AccountSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AutoFrameRateSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.ContentBlockSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.RemoteControlSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.GeneralSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.LanguageSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.MainUISettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.PlayerSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.SearchSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.SubtitleSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem.VideoPreset;
import com.liskovsoft.smartyoutubetv2.common.prefs.ContentBlockData;

import java.util.ArrayList;
import java.util.List;

public class AppDataSourceManager {
    private static AppDataSourceManager sInstance;

    private AppDataSourceManager() {
    }

    public static AppDataSourceManager instance() {
        if (sInstance == null) {
            sInstance = new AppDataSourceManager();
        }

        return sInstance;
    }

    public List<SettingsItem> getSettingItems(BasePresenter<?> presenter) {
        List<SettingsItem> settingItems = new ArrayList<>();
        Context context = presenter.getContext();

        settingItems.add(new SettingsItem(
                context.getString(R.string.settings_accounts), () -> AccountSettingsPresenter.instance(presenter.getContext()).show(), R.drawable.settings_account));
        settingItems.add(new SettingsItem(
                context.getString(R.string.settings_linked_devices), () -> RemoteControlSettingsPresenter.instance(presenter.getContext()).show(), R.drawable.settings_cast));
        settingItems.add(new SettingsItem(
                context.getString(R.string.settings_language_country), () -> LanguageSettingsPresenter.instance(presenter.getContext()).show(), R.drawable.settings_language));
        settingItems.add(new SettingsItem(
                context.getString(R.string.settings_general), () -> GeneralSettingsPresenter.instance(presenter.getContext()).show(), R.drawable.settings_app));
        settingItems.add(new SettingsItem(
                context.getString(R.string.settings_main_ui), () -> MainUISettingsPresenter.instance(presenter.getContext()).show(), R.drawable.settings_main_ui));
        //settingItems.add(new SettingsItem(
        //        context.getString(R.string.settings_ui_scale), () -> UIScaleSettingsPresenter.instance(presenter.getContext()).show(), R.drawable.settings_ui_scale));
        settingItems.add(new SettingsItem(
                context.getString(R.string.settings_player), () -> PlayerSettingsPresenter.instance(presenter.getContext()).show(), R.drawable.settings_player));
        settingItems.add(new SettingsItem(
                context.getString(R.string.auto_frame_rate), () -> AutoFrameRateSettingsPresenter.instance(presenter.getContext()).show(), R.drawable.settings_afr));
        settingItems.add(new SettingsItem(
                context.getString(R.string.subtitle_category_title), () -> SubtitleSettingsPresenter.instance(presenter.getContext()).show(), R.drawable.settings_subtitles));
        settingItems.add(new SettingsItem(
                context.getString(R.string.settings_search), () -> SearchSettingsPresenter.instance(presenter.getContext()).show(), R.drawable.settings_search));
        settingItems.add(new SettingsItem(
                ContentBlockData.SPONSOR_BLOCK_NAME, () -> ContentBlockSettingsPresenter.instance(presenter.getContext()).show(), R.drawable.settings_block));
        settingItems.add(new SettingsItem(
                context.getString(R.string.settings_about), () -> AboutSettingsPresenter.instance(presenter.getContext()).show(), R.drawable.settings_about));

        return settingItems;
    }

    //public VideoPreset[] getVideoPresets() {
    //    VideoPreset[] presets = {
    //            new VideoPreset("SD     30fps    avc", "854,480,30,avc"),
    //            new VideoPreset("SD     30fps    vp9", "854,480,30,vp9"),
    //            new VideoPreset("SD     60fps    avc", "854,480,60,avc"),
    //            new VideoPreset("SD     60fps    vp9", "854,480,60,vp9"),
    //            new VideoPreset("HD     30fps    avc", "1280,720,30,avc"),
    //            new VideoPreset("HD     30fps    vp9", "1280,720,30,vp9"),
    //            new VideoPreset("HD     60fps    avc", "1280,720,60,avc"),
    //            new VideoPreset("HD     60fps    vp9", "1280,720,60,vp9"),
    //            new VideoPreset("FHD    30fps    av01", "1920,1080,30,av01"),
    //            new VideoPreset("FHD    60fps    av01", "1920,1080,60,av01"),
    //            new VideoPreset("FHD    30fps    avc", "1920,1080,30,avc"),
    //            new VideoPreset("FHD    60fps    avc", "1920,1080,60,avc"),
    //            new VideoPreset("FHD    30fps    vp9", "1920,1080,30,vp9"),
    //            new VideoPreset("FHD    60fps    vp9", "1920,1080,60,vp9"),
    //            new VideoPreset("FHD    30fps    vp9+hdr", "1920,1080,30,vp9.2"),
    //            new VideoPreset("FHD    60fps    vp9+hdr", "1920,1080,60,vp9.2"),
    //            new VideoPreset("2K     30fps    av01", "2560,1440,30,av01"),
    //            new VideoPreset("2K     60fps    av01", "2560,1440,60,av01"),
    //            new VideoPreset("2K     30fps    vp9", "2560,1440,30,vp9"),
    //            new VideoPreset("2K     60fps    vp9", "2560,1440,60,vp9"),
    //            new VideoPreset("2K     30fps    vp9+hdr", "2560,1440,30,vp9.2"),
    //            new VideoPreset("2K     60fps    vp9+hdr", "2560,1440,60,vp9.2"),
    //            new VideoPreset("4K     30fps    av01", "3840,2160,30,av01"),
    //            new VideoPreset("4K     60fps    av01", "3840,2160,60,av01"),
    //            new VideoPreset("4K     30fps    vp9", "3840,2160,30,vp9"),
    //            new VideoPreset("4K     60fps    vp9", "3840,2160,60,vp9"),
    //            new VideoPreset("4K     30fps    vp9+hdr", "3840,2160,30,vp9.2"),
    //            new VideoPreset("4K     60fps    vp9+hdr", "3840,2160,60,vp9.2"),
    //            new VideoPreset("8K     30fps    av01", "7680,4320,30,av01"),
    //            new VideoPreset("8K     60fps    av01", "7680,4320,60,av01"),
    //            new VideoPreset("8K     30fps    vp9", "7680,4320,30,vp9"),
    //            new VideoPreset("8K     60fps    vp9", "7680,4320,60,vp9"),
    //            new VideoPreset("8K     30fps    vp9+hdr", "7680,4320,30,vp9.2"),
    //            new VideoPreset("8K     60fps    vp9+hdr", "7680,4320,60,vp9.2")
    //    };
    //
    //    return presets;
    //}

    public VideoPreset[] getVideoPresets() {
        VideoPreset[] presets = {
                new VideoPreset("480p     30fps    avc", "854,480,30,avc"),
                new VideoPreset("480p     30fps    vp9", "854,480,30,vp9"),
                new VideoPreset("480p     60fps    avc", "854,480,60,avc"),
                new VideoPreset("480p     60fps    vp9", "854,480,60,vp9"),
                new VideoPreset("720p     30fps    avc", "1280,720,30,avc"),
                new VideoPreset("720p     30fps    vp9", "1280,720,30,vp9"),
                new VideoPreset("720p     60fps    avc", "1280,720,60,avc"),
                new VideoPreset("720p     60fps    vp9", "1280,720,60,vp9"),
                new VideoPreset("1080p    30fps    av01", "1920,1080,30,av01"),
                new VideoPreset("1080p    60fps    av01", "1920,1080,60,av01"),
                new VideoPreset("1080p    30fps    avc", "1920,1080,30,avc"),
                new VideoPreset("1080p    60fps    avc", "1920,1080,60,avc"),
                new VideoPreset("1080p    30fps    vp9", "1920,1080,30,vp9"),
                new VideoPreset("1080p    60fps    vp9", "1920,1080,60,vp9"),
                new VideoPreset("1080p    30fps    vp9+hdr", "1920,1080,30,vp9.2"),
                new VideoPreset("1080p    60fps    vp9+hdr", "1920,1080,60,vp9.2"),
                new VideoPreset("1440p    30fps    av01", "2560,1440,30,av01"),
                new VideoPreset("1440p    60fps    av01", "2560,1440,60,av01"),
                new VideoPreset("1440p    30fps    vp9", "2560,1440,30,vp9"),
                new VideoPreset("1440p    60fps    vp9", "2560,1440,60,vp9"),
                new VideoPreset("1440p    30fps    vp9+hdr", "2560,1440,30,vp9.2"),
                new VideoPreset("1440p    60fps    vp9+hdr", "2560,1440,60,vp9.2"),
                new VideoPreset("2160p    30fps    av01", "3840,2160,30,av01"),
                new VideoPreset("2160p    60fps    av01", "3840,2160,60,av01"),
                new VideoPreset("2160p    30fps    vp9", "3840,2160,30,vp9"),
                new VideoPreset("2160p    60fps    vp9", "3840,2160,60,vp9"),
                new VideoPreset("2160p    30fps    vp9+hdr", "3840,2160,30,vp9.2"),
                new VideoPreset("2160p    60fps    vp9+hdr", "3840,2160,60,vp9.2"),
                new VideoPreset("4320p    30fps    av01", "7680,4320,30,av01"),
                new VideoPreset("4320p    60fps    av01", "7680,4320,60,av01"),
                new VideoPreset("4320p    30fps    vp9", "7680,4320,30,vp9"),
                new VideoPreset("4320p    60fps    vp9", "7680,4320,60,vp9"),
                new VideoPreset("4320p    30fps    vp9+hdr", "7680,4320,30,vp9.2"),
                new VideoPreset("4320p    60fps    vp9+hdr", "7680,4320,60,vp9.2")
        };

        return presets;
    }
}
