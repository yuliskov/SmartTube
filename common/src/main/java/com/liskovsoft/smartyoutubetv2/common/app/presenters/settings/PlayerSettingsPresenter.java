package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Pair;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorUtil;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

import java.util.ArrayList;
import java.util.List;

public class PlayerSettingsPresenter extends BasePresenter<Void> {
    private final PlayerData mPlayerData;
    private final PlayerTweaksData mPlayerTweaksData;
    private final SearchData mSearchData;
    private final GeneralData mGeneralData;
    private final SidebarService mSidebarService;
    private final MediaServiceData mMediaServiceData;
    private boolean mRestartApp;

    private PlayerSettingsPresenter(Context context) {
        super(context);
        mPlayerData = PlayerData.instance(context);
        mPlayerTweaksData = PlayerTweaksData.instance(context);
        mSearchData = SearchData.instance(context);
        mGeneralData = GeneralData.instance(context);
        mSidebarService = SidebarService.instance(context);
        mMediaServiceData = MediaServiceData.instance();
    }

    public static PlayerSettingsPresenter instance(Context context) {
        return new PlayerSettingsPresenter(context);
    }

    public void show() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appendPlaybackModeCategory(settingsPresenter);
        appendVideoPresetsCategory(settingsPresenter);
        appendPlayerButtonsCategory(settingsPresenter);
        appendNetworkEngineCategory(settingsPresenter);
        appendVideoBufferCategory(settingsPresenter);
        appendVideoZoomCategory(settingsPresenter);
        appendVideoSpeedCategory(settingsPresenter);
        appendAudioLanguageCategory(settingsPresenter);
        appendAudioShiftCategory(settingsPresenter);
        appendMasterVolumeCategory(settingsPresenter);
        appendOKButtonCategory(settingsPresenter);
        appendUIAutoHideCategory(settingsPresenter);
        appendSeekTypeCategory(settingsPresenter);
        appendSeekingPreviewCategory(settingsPresenter);
        AppDialogUtil.appendSeekIntervalDialogItems(getContext(), settingsPresenter, mPlayerData, false);
        //appendRememberSpeedCategory(settingsPresenter);
        appendScreenOffTimeoutCategory(settingsPresenter);
        appendEndingTimeCategory(settingsPresenter);
        appendPixelRatioCategory(settingsPresenter);
        //appendPlayerExitCategory(settingsPresenter);
        appendMiscCategory(settingsPresenter);
        appendDeveloperCategory(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.settings_player), () -> {
            if (mRestartApp) {
                mRestartApp = false;
                MessageHelpers.showLongMessage(getContext(), R.string.msg_restart_app);
            }
        });
    }

    private void appendOKButtonCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(
                getContext().getString(R.string.player_only_ui),
                option -> mPlayerData.setOKButtonBehavior(PlayerData.ONLY_UI),
                mPlayerData.getOKButtonBehavior() == PlayerData.ONLY_UI));

        options.add(UiOptionItem.from(
                getContext().getString(R.string.player_ui_and_pause),
                option -> mPlayerData.setOKButtonBehavior(PlayerData.UI_AND_PAUSE),
                mPlayerData.getOKButtonBehavior() == PlayerData.UI_AND_PAUSE));

        options.add(UiOptionItem.from(
                getContext().getString(R.string.player_only_pause),
                option -> mPlayerData.setOKButtonBehavior(PlayerData.ONLY_PAUSE),
                mPlayerData.getOKButtonBehavior() == PlayerData.ONLY_PAUSE));

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.player_ok_button_behavior), options);
    }

    @SuppressLint("StringFormatMatches")
    private void appendUIAutoHideCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(
                getContext().getString(R.string.option_never),
                option -> mPlayerData.setUiHideTimeoutSec(PlayerData.AUTO_HIDE_NEVER),
                mPlayerData.getUiHideTimeoutSec() == PlayerData.AUTO_HIDE_NEVER));

        for (int i = 1; i <= 15; i++) {
            int timeoutSec = i;
            options.add(UiOptionItem.from(
                    getContext().getString(R.string.ui_hide_timeout_sec, i),
                    option -> mPlayerData.setUiHideTimeoutSec(timeoutSec),
                    mPlayerData.getUiHideTimeoutSec() == i));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.player_ui_hide_behavior), options);
    }

    private void appendVideoBufferCategory(AppDialogPresenter settingsPresenter) {
        OptionCategory category = AppDialogUtil.createVideoBufferCategory(getContext());
        settingsPresenter.appendCategory(category);
    }

    private void appendVideoPresetsCategory(AppDialogPresenter settingsPresenter) {
        OptionCategory category = AppDialogUtil.createVideoPresetsCategory(getContext());
        settingsPresenter.appendCategory(category);
    }

    private void appendVideoZoomCategory(AppDialogPresenter settingsPresenter) {
        OptionCategory category = AppDialogUtil.createVideoZoomCategory(getContext());
        settingsPresenter.appendCategory(category);
    }

    private void appendAudioLanguageCategory(AppDialogPresenter settingsPresenter) {
        OptionCategory category = AppDialogUtil.createAudioLanguageCategory(getContext());
        settingsPresenter.appendCategory(category);
    }

    private void appendAudioShiftCategory(AppDialogPresenter settingsPresenter) {
        OptionCategory category = AppDialogUtil.createAudioShiftCategory(getContext());
        settingsPresenter.appendCategory(category);
    }

    private void appendMasterVolumeCategory(AppDialogPresenter settingsPresenter) {
        OptionCategory category = AppDialogUtil.createAudioVolumeCategory(getContext());
        settingsPresenter.appendCategory(category);
    }

    private void appendSeekingPreviewCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.player_seek_preview_none, PlayerData.SEEK_PREVIEW_NONE},
                {R.string.player_seek_preview_single, PlayerData.SEEK_PREVIEW_SINGLE},
                {R.string.player_seek_preview_carousel_slow, PlayerData.SEEK_PREVIEW_CAROUSEL_SLOW},
                {R.string.player_seek_preview_carousel_fast, PlayerData.SEEK_PREVIEW_CAROUSEL_FAST}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]),
                    optionItem -> mPlayerData.setSeekPreviewMode(pair[1]),
                    mPlayerData.getSeekPreviewMode() == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.player_seek_preview), options);
    }

    private void appendRememberSpeedCategory(AppDialogPresenter settingsPresenter) {
        OptionCategory category = AppDialogUtil.createRememberSpeedCategory(getContext());

        settingsPresenter.appendRadioCategory(category.title, category.options);
    }

    private void appendVideoSpeedCategory(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.video_speed), optionItem -> {
            AppDialogPresenter settingsPresenter2 = AppDialogPresenter.instance(getContext());
            settingsPresenter2.appendCategory(AppDialogUtil.createSpeedListCategory(getContext(), null));
            settingsPresenter2.appendCategory(AppDialogUtil.createRememberSpeedCategory(getContext()));
            settingsPresenter2.appendCategory(AppDialogUtil.createSpeedMiscCategory(getContext()));
            settingsPresenter2.showDialog(getContext().getString(R.string.video_speed));
        }));
    }

    private void appendScreenOffTimeoutCategory(AppDialogPresenter settingsPresenter) {
        OptionCategory category = AppDialogUtil.createPlayerScreenOffTimeoutCategory(getContext(), null);

        settingsPresenter.appendRadioCategory(category.title, category.options);
    }

    private void appendPlayerButtonsCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.auto_frame_rate, PlayerTweaksData.PLAYER_BUTTON_AFR},
                {R.string.action_sound_off, PlayerTweaksData.PLAYER_BUTTON_SOUND_OFF},
                {R.string.video_rotate, PlayerTweaksData.PLAYER_BUTTON_VIDEO_ROTATE},
                {R.string.video_flip, PlayerTweaksData.PLAYER_BUTTON_VIDEO_FLIP},
                {R.string.open_chat, PlayerTweaksData.PLAYER_BUTTON_CHAT},
                {R.string.content_block_provider, PlayerTweaksData.PLAYER_BUTTON_CONTENT_BLOCK},
                {R.string.seek_interval, PlayerTweaksData.PLAYER_BUTTON_SEEK_INTERVAL},
                {R.string.share_link, PlayerTweaksData.PLAYER_BUTTON_SHARE},
                {R.string.action_video_info, PlayerTweaksData.PLAYER_BUTTON_VIDEO_INFO},
                {R.string.action_video_stats, PlayerTweaksData.PLAYER_BUTTON_VIDEO_STATS},
                {R.string.action_playback_queue, PlayerTweaksData.PLAYER_BUTTON_PLAYBACK_QUEUE},
                {R.string.screen_dimming, PlayerTweaksData.PLAYER_BUTTON_SCREEN_DIMMING},
                {R.string.action_video_zoom, PlayerTweaksData.PLAYER_BUTTON_VIDEO_ZOOM},
                {R.string.action_channel, PlayerTweaksData.PLAYER_BUTTON_OPEN_CHANNEL},
                {R.string.action_search, PlayerTweaksData.PLAYER_BUTTON_SEARCH},
                {R.string.run_in_background, PlayerTweaksData.PLAYER_BUTTON_PIP},
                {R.string.action_video_speed, PlayerTweaksData.PLAYER_BUTTON_VIDEO_SPEED},
                {R.string.action_subtitles, PlayerTweaksData.PLAYER_BUTTON_SUBTITLES},
                {R.string.action_subscribe, PlayerTweaksData.PLAYER_BUTTON_SUBSCRIBE},
                {R.string.action_like, PlayerTweaksData.PLAYER_BUTTON_LIKE},
                {R.string.action_dislike, PlayerTweaksData.PLAYER_BUTTON_DISLIKE},
                {R.string.action_playlist_add, PlayerTweaksData.PLAYER_BUTTON_ADD_TO_PLAYLIST},
                {R.string.action_play_pause, PlayerTweaksData.PLAYER_BUTTON_PLAY_PAUSE},
                {R.string.action_repeat_mode, PlayerTweaksData.PLAYER_BUTTON_REPEAT_MODE},
                {R.string.action_next, PlayerTweaksData.PLAYER_BUTTON_NEXT},
                {R.string.action_previous, PlayerTweaksData.PLAYER_BUTTON_PREVIOUS},
                {R.string.playback_settings, PlayerTweaksData.PLAYER_BUTTON_HIGH_QUALITY}
        }) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]), optionItem -> {
                if (optionItem.isSelected()) {
                    mPlayerTweaksData.setPlayerButtonEnabled(pair[1]);
                } else {
                    mPlayerTweaksData.setPlayerButtonDisabled(pair[1]);
                }
            }, mPlayerTweaksData.isPlayerButtonEnabled(pair[1])));
        }

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.player_buttons), options);
    }

    private void appendDeveloperCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.disable_network_error_fixing),
                getContext().getString(R.string.disable_network_error_fixing_desc),
                option -> mPlayerTweaksData.setNetworkErrorFixingDisabled(option.isSelected()),
                mPlayerTweaksData.isNetworkErrorFixingDisabled()));

        // Oculus Quest fix: back button not closing the activity
        options.add(UiOptionItem.from(getContext().getString(R.string.oculus_quest_fix),
                option -> {
                    mPlayerTweaksData.setOculusQuestFixEnabled(option.isSelected());
                    mRestartApp = true;
                },
                mPlayerTweaksData.isOculusQuestFixEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.prefer_ipv4),
                getContext().getString(R.string.prefer_ipv4_desc),
                option -> {
                    mPlayerTweaksData.setIPv4DnsPreferred(option.isSelected());
                    mRestartApp = true;
                },
                mPlayerTweaksData.isIPv4DnsPreferred()));

        // Disable long press on buggy controllers.
        options.add(UiOptionItem.from(getContext().getString(R.string.disable_ok_long_press),
                getContext().getString(R.string.disable_ok_long_press_desc),
                option -> mGeneralData.disableOkButtonLongPress(option.isSelected()),
                mGeneralData.isOkButtonLongPressDisabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.audio_sync_fix),
                getContext().getString(R.string.audio_sync_fix_desc),
                option -> mPlayerTweaksData.setAudioSyncFixEnabled(option.isSelected()),
                mPlayerTweaksData.isAudioSyncFixEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.ambilight_ratio_fix),
                getContext().getString(R.string.ambilight_ratio_fix_desc),
                option -> {
                    mPlayerTweaksData.setTextureViewEnabled(option.isSelected());
                    if (option.isSelected()) {
                        // Tunneled playback works only with SurfaceView
                        mPlayerTweaksData.setTunneledPlaybackEnabled(false);
                    }
                },
                mPlayerTweaksData.isTextureViewEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.unlock_high_bitrate_formats) + " " + TrackSelectorUtil.HIGH_BITRATE_MARK,
                option -> mPlayerTweaksData.setHighBitrateFormatsEnabled(option.isSelected()),
                mPlayerTweaksData.isHighBitrateFormatsEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.unlock_high_bitrate_audio_formats),
                option -> mPlayerTweaksData.setUnsafeAudioFormatsEnabled(option.isSelected()),
                mPlayerTweaksData.isUnsafeAudioFormatsEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.force_legacy_codecs),
                getContext().getString(R.string.force_legacy_codecs_desc),
                option -> mPlayerData.setLegacyCodecsForced(option.isSelected()),
                mPlayerData.isLegacyCodecsForced()));

        options.add(UiOptionItem.from(getContext().getString(R.string.live_stream_fix),
                getContext().getString(R.string.live_stream_fix_desc),
                option -> {
                    mPlayerTweaksData.setHlsStreamsForced(option.isSelected());
                },
                mPlayerTweaksData.isHlsStreamsForced()));

        options.add(UiOptionItem.from(getContext().getString(R.string.live_stream_fix_4k),
                getContext().getString(R.string.live_stream_fix_4k_desc),
                option -> {
                    mPlayerTweaksData.setDashUrlStreamsForced(option.isSelected());
                },
                mPlayerTweaksData.isDashUrlStreamsForced()));

        options.add(UiOptionItem.from(getContext().getString(R.string.disable_stream_buffer),
                getContext().getString(R.string.disable_stream_buffer_desc),
                option -> mPlayerTweaksData.setBufferOnStreamsDisabled(option.isSelected()),
                mPlayerTweaksData.isBufferOnStreamsDisabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.playback_notifications_fix),
                getContext().getString(R.string.playback_notifications_fix_desc),
                option -> mPlayerTweaksData.setPlaybackNotificationsDisabled(option.isSelected()),
                mPlayerTweaksData.isPlaybackNotificationsDisabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.unlock_all_formats),
                getContext().getString(R.string.unlock_all_formats_desc),
                option -> mPlayerTweaksData.setAllFormatsUnlocked(option.isSelected()),
                mPlayerTweaksData.isAllFormatsUnlocked()));

        options.add(UiOptionItem.from(getContext().getString(R.string.alt_presets_behavior),
                getContext().getString(R.string.alt_presets_behavior_desc),
                option -> mPlayerTweaksData.setNoFpsPresetsEnabled(option.isSelected()),
                mPlayerTweaksData.isNoFpsPresetsEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.prefer_avc_over_vp9),
                getContext().getString(R.string.prefer_avc_over_vp9_desc),
                option -> mPlayerTweaksData.setAvcOverVp9Preferred(option.isSelected()),
                mPlayerTweaksData.isAvcOverVp9Preferred()));

        options.add(UiOptionItem.from(getContext().getString(R.string.amlogic_fix),
                getContext().getString(R.string.amlogic_fix_desc),
                option -> mPlayerTweaksData.setAmlogicFixEnabled(option.isSelected()),
                mPlayerTweaksData.isAmlogicFixEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.tunneled_video_playback),
                getContext().getString(R.string.tunneled_video_playback_desc),
                option -> {
                    mPlayerTweaksData.setTunneledPlaybackEnabled(option.isSelected());
                    if (option.isSelected()) {
                        // Tunneled playback works only with SurfaceView
                        mPlayerTweaksData.setTextureViewEnabled(false);
                    }
                },
                mPlayerTweaksData.isTunneledPlaybackEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.disable_vsync),
                getContext().getString(R.string.disable_vsync_desc),
                option -> mPlayerTweaksData.setSnappingToVsyncDisabled(option.isSelected()),
                mPlayerTweaksData.isSnappingToVsyncDisabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.skip_codec_profile_check),
                getContext().getString(R.string.skip_codec_profile_check_desc),
                option -> mPlayerTweaksData.setProfileLevelCheckSkipped(option.isSelected()),
                mPlayerTweaksData.isProfileLevelCheckSkipped()));

        options.add(UiOptionItem.from(getContext().getString(R.string.force_sw_codec),
                getContext().getString(R.string.force_sw_codec_desc),
                option -> mPlayerTweaksData.setSWDecoderForced(option.isSelected()),
                mPlayerTweaksData.isSWDecoderForced()));

        options.add(UiOptionItem.from(getContext().getString(R.string.sony_frame_drop_fix),
                getContext().getString(R.string.sony_frame_drop_fix_desc),
                option -> mPlayerTweaksData.setSonyFrameDropFixEnabled(option.isSelected()),
                mPlayerTweaksData.isSonyFrameDropFixEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.amazon_frame_drop_fix),
                getContext().getString(R.string.amazon_frame_drop_fix_desc),
                option -> mPlayerTweaksData.setAmazonFrameDropFixEnabled(option.isSelected()),
                mPlayerTweaksData.isAmazonFrameDropFixEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.keep_finished_activities),
                option -> mPlayerTweaksData.setKeepFinishedActivityEnabled(option.isSelected()),
                mPlayerTweaksData.isKeepFinishedActivityEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.disable_channels_service),
                option -> GlobalPreferences.instance(getContext()).setChannelsServiceEnabled(!option.isSelected()),
                !GlobalPreferences.instance(getContext()).isChannelsServiceEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_settings_section),
                option -> {
                    mSidebarService.enableSettingsSection(!option.isSelected());
                    mRestartApp = true;
                },
                !mSidebarService.isSettingsSectionEnabled()));

        // Disabled inside RetrofitHelper
        //options.add(UiOptionItem.from("Prefer IPv4 DNS",
        //        option -> GlobalPreferences.instance(getContext()).preferIPv4Dns(option.isSelected()),
        //        GlobalPreferences.instance(getContext()).isIPv4DnsPreferred()));
        //
        //options.add(UiOptionItem.from("Enable DNS over HTTPS",
        //        option -> GlobalPreferences.instance(getContext()).enableDnsOverHttps(option.isSelected()),
        //        GlobalPreferences.instance(getContext()).isDnsOverHttpsEnabled()));

        // Need to be enabled on older version of ExoPlayer (e.g. 2.10.6).
        // It's because there's no tweaks for modern devices.
        //options.add(UiOptionItem.from("Enable set output surface workaround",
        //        option -> mPlayerTweaksData.enableSetOutputSurfaceWorkaround(option.isSelected()),
        //        mPlayerTweaksData.isSetOutputSurfaceWorkaroundEnabled()));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.player_tweaks), options);
    }

    private void appendSeekTypeCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.player_seek_regular),
                option -> {
                    mPlayerData.setSeekConfirmPauseEnabled(false);
                    mPlayerData.setSeekConfirmPlayEnabled(false);
                },
                !mPlayerData.isSeekConfirmPauseEnabled() && !mPlayerData.isSeekConfirmPlayEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_seek_confirmation_pause),
                option -> {
                    mPlayerData.setSeekConfirmPauseEnabled(true);
                    mPlayerData.setSeekConfirmPlayEnabled(false);
                },
                mPlayerData.isSeekConfirmPauseEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_seek_confirmation_play),
                option -> {
                    mPlayerData.setSeekConfirmPauseEnabled(false);
                    mPlayerData.setSeekConfirmPlayEnabled(true);
                },
                mPlayerData.isSeekConfirmPlayEnabled()));

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.player_seek_type), options);
    }

    private void appendEndingTimeCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.option_disabled),
                option -> {
                    mPlayerData.setRemainingTimeEnabled(false);
                    mPlayerData.setEndingTimeEnabled(false);
                },
                !mPlayerData.isRemainingTimeEnabled() && !mPlayerData.isEndingTimeEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_show_remaining_time),
                option -> {
                    mPlayerData.setRemainingTimeEnabled(true);
                    mPlayerData.setEndingTimeEnabled(false);
                },
                mPlayerData.isRemainingTimeEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_show_ending_time),
                option -> {
                    mPlayerData.setEndingTimeEnabled(true);
                    mPlayerData.setRemainingTimeEnabled(false);
                },
                mPlayerData.isEndingTimeEnabled()));

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.player_show_ending_time), options);
    }

    private void appendPixelRatioCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        ArrayList<Pair<String, Float>> pairs = new ArrayList<>();
        pairs.add(new Pair<>("1:1 (16:9 display)", 1.0f));
        pairs.add(new Pair<>("1.11111:1 (16:10 display)", 1.11111f));
        pairs.add(new Pair<>("1.3333:1 (4:3 display)", 1.3333f));
        // There is no display with exact 21:9 proportion???
        //pairs.add(new Pair<>("0.7619:1 (21:9 display)", 0.7619f));
        pairs.add(new Pair<>("0.75:1 (64:27 display)", 0.75f));
        pairs.add(new Pair<>("0.7442:1 (43:18 display)", 0.7442f));
        pairs.add(new Pair<>("0.7407:1 (12:5 display)", 0.7407f));

        for (Pair<String, Float> pair : pairs) {
            options.add(UiOptionItem.from(pair.first,
                    optionItem -> mPlayerTweaksData.setPixelRatio(pair.second),
                    Helpers.floatEquals(mPlayerTweaksData.getPixelRatio(), pair.second)));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.player_pixel_ratio), options);
    }

    private void appendPlaybackModeCategory(AppDialogPresenter settingsPresenter) {
        OptionCategory category = AppDialogUtil.createPlaybackModeCategory(getContext());
        settingsPresenter.appendCategory(category);
    }

    private void appendNetworkEngineCategory(AppDialogPresenter settingsPresenter) {
        OptionCategory category = AppDialogUtil.createNetworkEngineCategory(getContext());
        settingsPresenter.appendCategory(category);
    }

    private void appendMiscCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.dont_resize_video_to_fit_dialog),
                option -> mPlayerTweaksData.setDontResizeVideoToFitDialogEnabled(option.isSelected()),
                mPlayerTweaksData.isDontResizeVideoToFitDialogEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_audio_focus),
                option -> mPlayerTweaksData.setAudioFocusEnabled(option.isSelected()),
                mPlayerTweaksData.isAudioFocusEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_auto_volume),
                option -> mPlayerTweaksData.setPlayerAutoVolumeEnabled(option.isSelected()),
                mPlayerTweaksData.isPlayerAutoVolumeEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_section_playlist),
                option -> mPlayerTweaksData.setSectionPlaylistEnabled(option.isSelected()),
                mPlayerTweaksData.isSectionPlaylistEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_chapter_notification),
                option -> mPlayerTweaksData.setChapterNotificationEnabled(option.isSelected()),
                mPlayerTweaksData.isChapterNotificationEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.sleep_timer),
                //getContext().getString(R.string.sleep_timer_desc),
                option -> mPlayerData.setSleepTimerEnabled(option.isSelected()),
                mPlayerData.isSleepTimerEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.search_background_playback),
                option -> mSearchData.enableTempBackgroundMode(option.isSelected()),
                mSearchData.isTempBackgroundModeEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_exit_shortcut) + ": " + getContext().getString(R.string.app_double_back_exit),
                option -> mGeneralData.setPlayerExitShortcut(option.isSelected() ? GeneralData.EXIT_DOUBLE_BACK : GeneralData.EXIT_SINGLE_BACK),
                mGeneralData.getPlayerExitShortcut() == GeneralData.EXIT_DOUBLE_BACK));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_loop_shorts),
                option -> mPlayerTweaksData.setLoopShortsEnabled(option.isSelected()),
                mPlayerTweaksData.isLoopShortsEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.place_chat_left),
                option -> mPlayerTweaksData.setChatPlacedLeft(option.isSelected()),
                mPlayerTweaksData.isChatPlacedLeft()));

        options.add(UiOptionItem.from(getContext().getString(R.string.place_comments_left),
                option -> mPlayerTweaksData.setCommentsPlacedLeft(option.isSelected()),
                mPlayerTweaksData.isCommentsPlacedLeft()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_disable_suggestions),
                option -> mPlayerTweaksData.setSuggestionsDisabled(option.isSelected()),
                mPlayerTweaksData.isSuggestionsDisabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_number_key_seek),
                option -> mPlayerData.setNumberKeySeekEnabled(option.isSelected()),
                mPlayerData.isNumberKeySeekEnabled()));

        //options.add(UiOptionItem.from(getContext().getString(R.string.app_corner_clock),
        //        option -> {
        //            mGeneralData.enableGlobalClock(option.isSelected());
        //            mRestartApp = true;
        //        },
        //        mGeneralData.isGlobalClockEnabled()));
        //
        //options.add(UiOptionItem.from(getContext().getString(R.string.player_corner_clock),
        //        option -> mPlayerData.enableGlobalClock(option.isSelected()),
        //        mPlayerData.isGlobalClockEnabled()));
        //
        //options.add(UiOptionItem.from(getContext().getString(R.string.player_corner_ending_time),
        //        option -> mPlayerData.enableGlobalEndingTime(option.isSelected()),
        //        mPlayerData.isGlobalEndingTimeEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.remember_position_of_live_videos),
                option -> mPlayerTweaksData.setRememberPositionOfLiveVideosEnabled(option.isSelected()),
                mPlayerTweaksData.isRememberPositionOfLiveVideosEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.remember_position_of_short_videos),
                option -> mPlayerTweaksData.setRememberPositionOfShortVideosEnabled(option.isSelected()),
                mPlayerTweaksData.isRememberPositionOfShortVideosEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_speed_button_old_behavior),
                option -> mPlayerTweaksData.setSpeedButtonOldBehaviorEnabled(option.isSelected()),
                mPlayerTweaksData.isSpeedButtonOldBehaviorEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_show_clock),
                option -> mPlayerData.setClockEnabled(option.isSelected()),
                mPlayerData.isClockEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_show_quality_info),
                option -> mPlayerData.setQualityInfoEnabled(option.isSelected()),
                mPlayerData.isQualityInfoEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_show_quality_info_bitrate),
                option -> mPlayerTweaksData.setQualityInfoBitrateEnabled(option.isSelected()),
                mPlayerTweaksData.isQualityInfoBitrateEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_global_focus),
                getContext().getString(R.string.player_global_focus_desc),
                option -> mPlayerTweaksData.setSimplePlayerNavigationEnabled(option.isSelected()),
                mPlayerTweaksData.isSimplePlayerNavigationEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_ui_on_next),
                option -> mPlayerTweaksData.setPlayerUiOnNextEnabled(option.isSelected()),
                mPlayerTweaksData.isPlayerUiOnNextEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_ui_animations),
                option -> mPlayerTweaksData.setUIAnimationsEnabled(option.isSelected()),
                mPlayerTweaksData.isUIAnimationsEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_likes_count),
                option -> mPlayerTweaksData.setLikesCounterEnabled(option.isSelected()),
                mPlayerTweaksData.isLikesCounterEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_show_tooltips),
                option -> mPlayerData.setTooltipsEnabled(option.isSelected()),
                mPlayerData.isTooltipsEnabled()));

        // See: Utils.updateTooltip
        //options.add(UiOptionItem.from(getContext().getString(R.string.player_show_tooltips) + ": " + getContext().getString(R.string.long_press_for_options),
        //        option -> {
        //            mGeneralData.enableFirstUseTooltip(option.isSelected());
        //            mRestartApp = true;
        //        },
        //        mGeneralData.isFirstUseTooltipEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_button_long_click),
                option -> mPlayerTweaksData.setButtonLongClickEnabled(option.isSelected()),
                mPlayerTweaksData.isButtonLongClickEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.real_channel_icon),
                option -> mPlayerTweaksData.setRealChannelIconEnabled(option.isSelected()),
                mPlayerTweaksData.isRealChannelIconEnabled()));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.player_other), options);
    }

    private void appendPlayerExitCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.app_double_back_exit, GeneralData.EXIT_DOUBLE_BACK},
                {R.string.app_single_back_exit, GeneralData.EXIT_SINGLE_BACK}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]),
                    optionItem -> mGeneralData.setPlayerExitShortcut(pair[1]),
                    mGeneralData.getPlayerExitShortcut() == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.player_exit_shortcut), options);
    }
}
