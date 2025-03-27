package com.liskovsoft.smartyoutubetv2.common.utils;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;

import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.data.ItemGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.PlaylistInfo;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.helpers.PermissionHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerEngineConstants;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter.VideoMenuCallback;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.channelgroup.ChannelGroupServiceWrapper;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleManager.SubtitleStyle;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem.VideoPreset;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack;
import com.liskovsoft.smartyoutubetv2.common.misc.AppDataSourceManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.common.prefs.ContentBlockData;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.youtubeapi.service.YouTubeMediaItemService;
import com.liskovsoft.youtubeapi.playlist.impl.YouTubePlaylistInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import arte.programar.materialfile.MaterialFilePicker;
import arte.programar.materialfile.ui.FilePickerActivity;
import arte.programar.materialfile.utils.FileUtils;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

public class AppDialogUtil {
    private static final String TAG = AppDialogUtil.class.getSimpleName();
    private static final int VIDEO_BUFFER_ID = 134;
    private static final int BACKGROUND_PLAYBACK_ID = 135;
    private static final int VIDEO_PRESETS_ID = 136;
    private static final int AUDIO_DELAY_ID = 137;
    private static final int AUDIO_LANGUAGE_ID = 138;
    private static final int PLAYER_SCREEN_TIMEOUT_ID = 139;
    private static final int PLAYER_SCREEN_DIMMING_ID = 140;
    private static final int PLAYER_SPEED_LIST_ID = 141;
    private static final int PLAYER_REMEMBER_SPEED_ID = 142;
    private static final int PLAYER_SPEED_MISC_ID = 143;
    private static final int PITCH_EFFECT_ID = 144;
    private static final int AUDIO_VOLUME_ID = 145;
    private static final int PLAYER_REPEAT_ID = 146;
    private static final int PLAYER_ENGINE_ID = 147;
    private static final int SUBTITLE_STYLES_ID = 45;
    private static final int SUBTITLE_SIZE_ID = 46;
    private static final int SUBTITLE_POSITION_ID = 47;
    private static final int FILE_PICKER_REQUEST_CODE = 205;

    /**
     * Adds share link items to existing dialog.
     */
    public static void appendShareLinkDialogItem(Context context, AppDialogPresenter dialogPresenter, Video video) {
        appendShareLinkDialogItem(context, dialogPresenter, video, -1);
    }

    /**
     * Adds share link items to existing dialog.
     */
    public static void appendShareLinkDialogItem(Context context, AppDialogPresenter dialogPresenter, Video video, int positionSec) {
        if (video == null) {
            return;
        }

        if (video.videoId == null && video.channelId == null) {
            return;
        }

        dialogPresenter.appendSingleButton(
                UiOptionItem.from(context.getString(R.string.share_link), optionItem -> {
                    if (video.videoId != null) {
                        Utils.displayShareVideoDialog(context, video.videoId, positionSec == -1 ? Utils.toSec(video.getPositionMs()) : positionSec);
                    } else if (video.channelId != null) {
                        Utils.displayShareChannelDialog(context, video.channelId);
                    }
                }));
    }

    /**
     * Adds share link items to existing dialog.
     */
    public static void appendShareEmbedLinkDialogItem(Context context, AppDialogPresenter dialogPresenter, Video video) {
        appendShareEmbedLinkDialogItem(context, dialogPresenter, video, -1);
    }

    /**
     * Adds share link items to existing dialog.
     */
    public static void appendShareEmbedLinkDialogItem(Context context, AppDialogPresenter dialogPresenter, Video video, int positionSec) {
        if (video == null) {
            return;
        }

        if (video.videoId == null) {
            return;
        }

        dialogPresenter.appendSingleButton(
                UiOptionItem.from(context.getString(R.string.share_embed_link), optionItem -> {
                    if (video.videoId != null) {
                        Utils.displayShareEmbedVideoDialog(context, video.videoId, positionSec == -1 ? Utils.toSec(video.getPositionMs()) : positionSec);
                    }
                }));
    }

    /**
     * Adds QR code item to existing dialog.
     */
    public static void appendShareQRLinkDialogItem(Context context, AppDialogPresenter dialogPresenter, Video video) {
        appendShareQRLinkDialogItem(context, dialogPresenter, video, -1);
    }

    /**
     * Adds QR code item to existing dialog.
     */
    public static void appendShareQRLinkDialogItem(Context context, AppDialogPresenter dialogPresenter, Video video, int positionSec) {
        if (video == null) {
            return;
        }

        if (video.videoId == null) {
            return;
        }

        dialogPresenter.appendSingleButton(
                UiOptionItem.from(context.getString(R.string.share_qr_link), optionItem -> {
                    dialogPresenter.closeDialog(); // pause bg video
                    if (video.videoId != null) {
                        Utils.openLink(context, Utils.toQrCodeLink(
                                Utils.convertToFullVideoUrl(video.videoId, positionSec == -1 ? Utils.toSec(video.getPositionMs()) : positionSec).toString()));
                    }
                }));
    }

    public static OptionCategory createBackgroundPlaybackCategory(Context context, PlayerData playerData, GeneralData generalData) {
        return createBackgroundPlaybackCategory(context, playerData, generalData, () -> {});
    }

    public static OptionCategory createBackgroundPlaybackCategory(Context context, PlayerData playerData, GeneralData generalData, Runnable onSetCallback) {
        String categoryTitle = context.getString(R.string.category_background_playback);

        List<OptionItem> options = new ArrayList<>();
        options.add(UiOptionItem.from(context.getString(R.string.option_background_playback_off),
                optionItem -> {
                    playerData.setBackgroundMode(PlayerData.BACKGROUND_MODE_DEFAULT);
                    generalData.setBackgroundPlaybackShortcut(GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME);
                    onSetCallback.run();
                }, playerData.getBackgroundMode() == PlayerData.BACKGROUND_MODE_DEFAULT));

        if (Helpers.isPictureInPictureSupported(context)) {
            String pip = context.getString(R.string.option_background_playback_pip);
            options.add(UiOptionItem.from(String.format("%s (%s)", pip, context.getString(R.string.pressing_home)),
                    optionItem -> {
                        playerData.setBackgroundMode(PlayerData.BACKGROUND_MODE_PIP);
                        generalData.setBackgroundPlaybackShortcut(GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME);
                        onSetCallback.run();
                    }, playerData.getBackgroundMode() == PlayerData.BACKGROUND_MODE_PIP &&
                    generalData.getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME));

            options.add(UiOptionItem.from(String.format("%s (%s)", pip, context.getString(R.string.pressing_home_back)),
                    optionItem -> {
                        playerData.setBackgroundMode(PlayerData.BACKGROUND_MODE_PIP);
                        generalData.setBackgroundPlaybackShortcut(GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME_BACK);
                        onSetCallback.run();
                    }, playerData.getBackgroundMode() == PlayerData.BACKGROUND_MODE_PIP &&
                    generalData.getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME_BACK));

            options.add(UiOptionItem.from(String.format("%s (%s)", pip, context.getString(R.string.pressing_back)),
                    optionItem -> {
                        playerData.setBackgroundMode(PlayerData.BACKGROUND_MODE_PIP);
                        generalData.setBackgroundPlaybackShortcut(GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_BACK);
                        onSetCallback.run();
                    }, playerData.getBackgroundMode() == PlayerData.BACKGROUND_MODE_PIP &&
                            generalData.getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_BACK));
        }

        String audio = context.getString(R.string.option_background_playback_only_audio);
        options.add(UiOptionItem.from(String.format("%s (%s)", audio, context.getString(R.string.pressing_home)),
                optionItem -> {
                    playerData.setBackgroundMode(PlayerData.BACKGROUND_MODE_SOUND);
                    generalData.setBackgroundPlaybackShortcut(GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME);
                    onSetCallback.run();
                }, playerData.getBackgroundMode() == PlayerData.BACKGROUND_MODE_SOUND &&
                    generalData.getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME));
        options.add(UiOptionItem.from(String.format("%s (%s)", audio, context.getString(R.string.pressing_home_back)),
                optionItem -> {
                    playerData.setBackgroundMode(PlayerData.BACKGROUND_MODE_SOUND);
                    generalData.setBackgroundPlaybackShortcut(GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME_BACK);
                    onSetCallback.run();
                }, playerData.getBackgroundMode() == PlayerData.BACKGROUND_MODE_SOUND &&
                    generalData.getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME_BACK));
        options.add(UiOptionItem.from(String.format("%s (%s)", audio, context.getString(R.string.pressing_back)),
                optionItem -> {
                    playerData.setBackgroundMode(PlayerData.BACKGROUND_MODE_SOUND);
                    generalData.setBackgroundPlaybackShortcut(GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_BACK);
                    onSetCallback.run();
                }, playerData.getBackgroundMode() == PlayerData.BACKGROUND_MODE_SOUND &&
                        generalData.getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_BACK));

        if (Helpers.isAndroidTV(context) && Build.VERSION.SDK_INT < 26) { // useful only for pre-Oreo UI
            String behind = context.getString(R.string.option_background_playback_behind);
            options.add(UiOptionItem.from(String.format("%s (%s - %s)", behind, "Android TV 5,6,7", context.getString(R.string.pressing_home)),
                    optionItem -> {
                        playerData.setBackgroundMode(PlayerData.BACKGROUND_MODE_PLAY_BEHIND);
                        generalData.setBackgroundPlaybackShortcut(GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME);
                        onSetCallback.run();
                    }, playerData.getBackgroundMode() == PlayerData.BACKGROUND_MODE_PLAY_BEHIND &&
                        generalData.getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME));
            options.add(UiOptionItem.from(String.format("%s (%s - %s)", behind, "Android TV 5,6,7", context.getString(R.string.pressing_home_back)),
                    optionItem -> {
                        playerData.setBackgroundMode(PlayerData.BACKGROUND_MODE_PLAY_BEHIND);
                        generalData.setBackgroundPlaybackShortcut(GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME_BACK);
                        onSetCallback.run();
                    }, playerData.getBackgroundMode() == PlayerData.BACKGROUND_MODE_PLAY_BEHIND &&
                        generalData.getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME_BACK));
        }

        return OptionCategory.from(BACKGROUND_PLAYBACK_ID, OptionCategory.TYPE_RADIO_LIST, categoryTitle, options);
    }

    public static OptionCategory createVideoPresetsCategory(Context context) {
        return createVideoPresetsCategory(context, () -> {});
    }

    public static OptionCategory createVideoPresetsCategory(Context context, Runnable onFormatSelected) {
        return OptionCategory.from(
                VIDEO_PRESETS_ID,
                OptionCategory.TYPE_RADIO_LIST,
                context.getString(R.string.title_video_presets),
                fromPresets(
                        context,
                        AppDataSourceManager.instance().getVideoPresets(),
                        onFormatSelected
                )
        );
    }

    private static List<OptionItem> fromPresets(Context context, VideoPreset[] presets, Runnable onFormatSelected) {
        List<OptionItem> result = new ArrayList<>();

        PlayerData playerData = PlayerData.instance(context);
        PlayerTweaksData playerTweaksData = PlayerTweaksData.instance(context);
        FormatItem selectedFormat = playerData.getFormat(FormatItem.TYPE_VIDEO);
        boolean isPresetSelection = selectedFormat != null && selectedFormat.isPreset();
        boolean isAllFormatsUnlocked = playerTweaksData.isAllFormatsUnlocked();

        for (VideoPreset preset : presets) {
            if (!isAllFormatsUnlocked && !Utils.isPresetSupported(preset)) {
                continue;
            }

            result.add(0, UiOptionItem.from(preset.name,
                    option -> setFormat(preset.format, playerData, onFormatSelected),
                    isPresetSelection && preset.format.equals(selectedFormat)));
        }

        FormatItem noVideo = ExoFormatItem.from(MediaTrack.forRendererIndex(TrackSelectorManager.RENDERER_INDEX_VIDEO), true);
        result.add(0, UiOptionItem.from(
                context.getString(R.string.video_disabled),
                optionItem ->
                        setFormat(noVideo, playerData, onFormatSelected),
                isPresetSelection && Helpers.equals(noVideo, selectedFormat)));

        result.add(0, UiOptionItem.from(
                context.getString(R.string.video_preset_disabled),
                optionItem -> setFormat(playerData.getDefaultVideoFormat(), playerData, onFormatSelected),
                !isPresetSelection));

        return result;
    }

    public static OptionCategory createVideoBufferCategory(Context context) {
        return createVideoBufferCategory(context, () -> {});
    }

    private static void setFormat(FormatItem formatItem, PlayerData playerData, Runnable onFormatSelected) {
        if (playerData.isLegacyCodecsForced()) {
            playerData.forceLegacyCodecs(false);
        }
        playerData.setFormat(formatItem);
        onFormatSelected.run();
    }

    public static OptionCategory createVideoBufferCategory(Context context, Runnable onBufferSelected) {
        PlayerData playerData = PlayerData.instance(context);
        String videoBufferTitle = context.getString(R.string.video_buffer);
        List<OptionItem> optionItems = new ArrayList<>();
        optionItems.add(createVideoBufferOption(context, playerData, R.string.video_buffer_size_low, PlayerData.BUFFER_LOW, onBufferSelected));
        optionItems.add(createVideoBufferOption(context, playerData, R.string.video_buffer_size_med, PlayerData.BUFFER_MEDIUM, onBufferSelected));
        optionItems.add(createVideoBufferOption(context, playerData, R.string.video_buffer_size_high, PlayerData.BUFFER_HIGH, onBufferSelected));
        optionItems.add(createVideoBufferOption(context, playerData, R.string.video_buffer_size_highest, PlayerData.BUFFER_HIGHEST, onBufferSelected));
        return OptionCategory.from(VIDEO_BUFFER_ID, OptionCategory.TYPE_RADIO_LIST, videoBufferTitle, optionItems);
    }

    private static OptionItem createVideoBufferOption(Context context, PlayerData playerData, int titleResId, int type, Runnable onBufferSelected) {
        return UiOptionItem.from(
                context.getString(titleResId),
                optionItem -> {
                    playerData.setVideoBufferType(type);
                    onBufferSelected.run();
                },
                playerData.getVideoBufferType() == type);
    }

    public static OptionCategory createAudioLanguageCategory(Context context) {
        return createAudioLanguageCategory(context, () -> {});
    }

    public static OptionCategory createAudioLanguageCategory(Context context, Runnable onSetCallback) {
        PlayerData playerData = PlayerData.instance(context);
        String title = context.getString(R.string.audio_language);

        List<OptionItem> options = new ArrayList<>();

        List<String> addedCodes = new ArrayList<>();
        List<String> lastLanguages = playerData.getLastAudioLanguages();

        for (Locale locale : Locale.getAvailableLocales()) {
            String languageCode = locale.getLanguage().toLowerCase();

            if (addedCodes.contains(languageCode) || lastLanguages.contains(languageCode)) {
                continue;
            }

            options.add(UiOptionItem.from(locale.getDisplayLanguage(),
                    optionItem -> {
                        playerData.setAudioLanguage(languageCode);
                        onSetCallback.run();
                    },
                    languageCode.equals(playerData.getAudioLanguage())));
            addedCodes.add(languageCode);
        }

        // NOTE: Comparator.comparing API >= 24
        // Alphabetical order
        Collections.sort(options, (o1, o2) -> ((String) o1.getTitle()).compareTo((String) o2.getTitle()));

        for (int i = 0; i < lastLanguages.size(); i++) {
            String languageCode = lastLanguages.get(i);
            Locale locale = new Locale(languageCode);

            options.add(i, UiOptionItem.from(locale.getDisplayLanguage(),
                    optionItem -> {
                        playerData.setAudioLanguage(languageCode);
                        onSetCallback.run();
                    },
                    languageCode.equals(playerData.getAudioLanguage())));
        }

        options.add(0, UiOptionItem.from(context.getString(R.string.default_lang),
                optionItem -> {
                    playerData.setAudioLanguage("");
                    onSetCallback.run();
                },
                "".equals(playerData.getAudioLanguage())));

        return OptionCategory.from(AUDIO_LANGUAGE_ID, OptionCategory.TYPE_RADIO_LIST, title, options);
    }

    public static OptionCategory createAudioShiftCategory(Context context) {
        return createAudioShiftCategory(context, () -> {});
    }

    public static OptionCategory createAudioShiftCategory(Context context, Runnable onSetCallback) {
        PlayerData playerData = PlayerData.instance(context);
        String title = context.getString(R.string.audio_shift);

        List<OptionItem> options = new ArrayList<>();

        for (int delayMs : Helpers.range(-8_000, 8_000, 50)) {
            options.add(UiOptionItem.from(context.getString(R.string.audio_shift_sec, Helpers.toString(delayMs / 1_000f)),
                    optionItem -> {
                        playerData.setAudioDelayMs(delayMs);
                        onSetCallback.run();
                    },
                    delayMs == playerData.getAudioDelayMs()));
        }

        return OptionCategory.from(AUDIO_DELAY_ID, OptionCategory.TYPE_RADIO_LIST, title, options);
    }

    public static OptionCategory createAudioVolumeCategory(Context context) {
        return createAudioVolumeCategory(context, () -> {});
    }

    public static OptionCategory createAudioVolumeCategory(Context context, Runnable onSetCallback) {
        PlayerData playerData = PlayerData.instance(context);
        String title = context.getString(R.string.player_volume);

        List<OptionItem> options = new ArrayList<>();

        for (int scalePercent : Helpers.range(0, 300, 5)) {
            float scale = scalePercent / 100f;
            options.add(UiOptionItem.from(String.format("%s%%", scalePercent),
                    optionItem -> {
                        playerData.setPlayerVolume(scale);

                        if (scalePercent > 100) {
                            MessageHelpers.showLongMessage(context, R.string.volume_boost_warning);
                        }

                        onSetCallback.run();
                    },
                    Helpers.floatEquals(scale, playerData.getPlayerVolume())));
        }

        return OptionCategory.from(AUDIO_VOLUME_ID, OptionCategory.TYPE_RADIO_LIST, title, options);
    }

    public static OptionCategory createPitchEffectCategory(Context context, PlayerManager playerManager, PlayerData playerData) {
        String title = context.getString(R.string.pitch_effect);

        List<OptionItem> options = new ArrayList<>();

        for (int pitchRaw : Helpers.range(1, 20 * 4, 1)) {
            float pitch = pitchRaw / (10f * 4);
            options.add(UiOptionItem.from(Helpers.toString(pitch),
                    optionItem -> {
                        playerManager.setPitch(pitch);
                        playerData.setPitch(pitch);
                    },
                    Helpers.floatEquals(pitch, playerManager.getPitch())));
        }

        return OptionCategory.from(PITCH_EFFECT_ID, OptionCategory.TYPE_RADIO_LIST, title, options);
    }

    public static OptionCategory createSubtitleStylesCategory(Context context) {
        String subtitleStyleTitle = context.getString(R.string.subtitle_style);

        return OptionCategory.from(SUBTITLE_STYLES_ID, OptionCategory.TYPE_RADIO_LIST, subtitleStyleTitle, createSubtitleStyles(context));
    }

    public static OptionItem createSubtitleChannelOption(Context context) {
        PlayerData playerData = PlayerData.instance(context);
        return UiOptionItem.from(context.getString(R.string.subtitle_remember),
                optionItem -> playerData.enableSubtitlesPerChannel(optionItem.isSelected()),
                playerData.isSubtitlesPerChannelEnabled()
        );
    }

    @TargetApi(19)
    private static List<OptionItem> createSubtitleStyles(Context context) {
        PlayerData playerData = PlayerData.instance(context);
        List<SubtitleStyle> subtitleStyles = playerData.getSubtitleStyles();
        List<OptionItem> styleOptions = new ArrayList<>();

        for (SubtitleStyle subtitleStyle : subtitleStyles) {
            styleOptions.add(UiOptionItem.from(
                    context.getString(subtitleStyle.nameResId),
                    option -> {
                        playerData.setSubtitleStyle(subtitleStyle);
                        Utils.showPlayerControls(context, false);
                    },
                    subtitleStyle.equals(playerData.getSubtitleStyle())));
        }

        return styleOptions;
    }

    public static OptionCategory createSubtitleSizeCategory(Context context) {
        PlayerData playerData = PlayerData.instance(context);
        List<OptionItem> options = new ArrayList<>();

        for (int scalePercent : Helpers.range(10, 200, 10)) {
            float scale = scalePercent / 100f;
            options.add(UiOptionItem.from(String.format("%sx", scale),
                    optionItem -> {
                        playerData.setSubtitleScale(scale);
                        Utils.showPlayerControls(context, false);
                    },
                    Helpers.floatEquals(scale, playerData.getSubtitleScale())));
        }

        return OptionCategory.from(SUBTITLE_SIZE_ID, OptionCategory.TYPE_RADIO_LIST, context.getString(R.string.subtitle_scale), options);
    }

    public static OptionCategory createSubtitlePositionCategory(Context context) {
        PlayerData playerData = PlayerData.instance(context);
        List<OptionItem> options = new ArrayList<>();

        for (int positionPercent : Helpers.range(0, 100, 5)) {
            float position = positionPercent / 100f;
            options.add(UiOptionItem.from(String.format("%s%%", positionPercent),
                    optionItem -> {
                        playerData.setSubtitlePosition(position);
                        Utils.showPlayerControls(context, false);
                    },
                    Helpers.floatEquals(position, playerData.getSubtitlePosition())));
        }

        return OptionCategory.from(SUBTITLE_POSITION_ID, OptionCategory.TYPE_RADIO_LIST, context.getString(R.string.subtitle_position), options);
    }

    public static OptionCategory createVideoZoomCategory(Context context) {
        return createVideoZoomCategory(context, () -> {});
    }

    public static OptionCategory createVideoZoomCategory(Context context, Runnable onSelectZoomMode) {
        PlayerData playerData = PlayerData.instance(context);
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.video_zoom_default, PlayerData.ZOOM_MODE_DEFAULT},
                {R.string.video_zoom_fit_width, PlayerData.ZOOM_MODE_FIT_WIDTH},
                {R.string.video_zoom_fit_height, PlayerData.ZOOM_MODE_FIT_HEIGHT},
                {R.string.video_zoom_fit_both, PlayerData.ZOOM_MODE_FIT_BOTH},
                {R.string.video_zoom_stretch, PlayerData.ZOOM_MODE_STRETCH}}) {
            options.add(UiOptionItem.from(context.getString(pair[0]),
                    optionItem -> {
                        playerData.setVideoZoomMode(pair[1]);
                        playerData.setVideoZoom(-1);
                        onSelectZoomMode.run();
                    },
                    playerData.getVideoZoomMode() == pair[1] && playerData.getVideoZoom() == -1));
        }

        // Zoom above 100% has centering problems with 2K-4K videos
        int[][] zoomRanges = {
            Helpers.range(50, 95, 5),  // from 50 to 95 in steps of 5
            Helpers.range(96, 100, 1), // from 96 to 100 in steps of 1
            Helpers.range(105, 300, 5) // from 105 to 300 in steps of 5
        };

        for (int[] zoomRange : zoomRanges) {
            for (int zoomPercents : zoomRange) {
                options.add(UiOptionItem.from(String.format("%s%%", zoomPercents),
                        optionItem -> {
                            playerData.setVideoZoom(zoomPercents);
                            playerData.setVideoZoomMode(PlayerData.ZOOM_MODE_DEFAULT);
                            onSelectZoomMode.run();
                        },
                        playerData.getVideoZoom() == zoomPercents));
            }
        }

        String videoZoomTitle = context.getString(R.string.video_zoom);

        return OptionCategory.from(SUBTITLE_STYLES_ID, OptionCategory.TYPE_RADIO_LIST, videoZoomTitle, options);
    }

    public static OptionCategory createVideoAspectCategory(Context context, PlayerData playerData, Runnable onSelectAspectMode) {
        List<OptionItem> options = new ArrayList<>();

        Map<String, Float> pairs = new LinkedHashMap<>();
        pairs.put(context.getString(R.string.video_zoom_default), PlayerData.ASPECT_RATIO_DEFAULT);
        pairs.put("1:1", PlayerData.ASPECT_RATIO_1_1);
        pairs.put("4:3", PlayerData.ASPECT_RATIO_4_3);
        pairs.put("5:4", PlayerData.ASPECT_RATIO_5_4);
        pairs.put("16:9", PlayerData.ASPECT_RATIO_16_9);
        pairs.put("16:10", PlayerData.ASPECT_RATIO_16_10);
        pairs.put("21:9 (2.33:1)", PlayerData.ASPECT_RATIO_21_9);
        pairs.put("64:27 (2.37:1)", PlayerData.ASPECT_RATIO_64_27);
        pairs.put("2.21:1", PlayerData.ASPECT_RATIO_221_1);
        pairs.put("2.35:1", PlayerData.ASPECT_RATIO_235_1);
        pairs.put("2.39:1", PlayerData.ASPECT_RATIO_239_1);

        for (Entry<String, Float> entry: pairs.entrySet()) {
            options.add(UiOptionItem.from(entry.getKey(),
                    optionItem -> {
                        playerData.setVideoAspectRatio(entry.getValue());
                        onSelectAspectMode.run();
                    }, Helpers.floatEquals(playerData.getVideoAspectRatio(), entry.getValue())));
        }

        String videoZoomTitle = context.getString(R.string.video_aspect);

        return OptionCategory.from(SUBTITLE_STYLES_ID, OptionCategory.TYPE_RADIO_LIST, videoZoomTitle, options);
    }

    public static OptionCategory createVideoRotateCategory(Context context, PlayerData playerData, Runnable onRotate) {
        List<OptionItem> options = new ArrayList<>();

        for (int angle : new int[] {0, 90, 180, 270}) {
            options.add(UiOptionItem.from(String.valueOf(angle),
                    optionItem -> {
                        playerData.setVideoRotation(angle);
                        onRotate.run();
                    }, playerData.getVideoRotation() == angle));
        }

        String videoRotateTitle = context.getString(R.string.video_rotate);

        return OptionCategory.from(SUBTITLE_STYLES_ID, OptionCategory.TYPE_RADIO_LIST, videoRotateTitle, options);
    }

    public static OptionCategory createPlayerScreenOffDimmingCategory(Context context, Runnable onApply) {
        PlayerTweaksData playerTweaksData = PlayerTweaksData.instance(context);
        List<OptionItem> options = new ArrayList<>();

        for (int dimPercents : Helpers.range(10, 100, 10)) {
            options.add(UiOptionItem.from(dimPercents + "%",
                    optionItem -> {
                        playerTweaksData.setScreenOffDimmingPercents(dimPercents);
                        if (onApply != null) {
                            onApply.run();
                        }
                    },
                    playerTweaksData.getScreenOffDimmingPercents() == dimPercents));
        }

        String title = context.getString(R.string.player_screen_off_dimming);

        return OptionCategory.from(PLAYER_SCREEN_DIMMING_ID, OptionCategory.TYPE_RADIO_LIST, title, options);
    }

    @SuppressLint("StringFormatMatches")
    public static OptionCategory createPlayerScreenOffTimeoutCategory(Context context, Runnable onApply) {
        PlayerTweaksData playerTweaksData = PlayerTweaksData.instance(context);
        List<OptionItem> options = new ArrayList<>();

        for (int timeoutSec : Helpers.range(0, 10, 1)) {
            options.add(UiOptionItem.from(timeoutSec == 0 ? context.getString(R.string.option_never) : context.getString(R.string.ui_hide_timeout_sec, timeoutSec),
                    optionItem -> {
                        playerTweaksData.setScreenOffTimeoutSec(timeoutSec);
                        if (onApply != null) {
                            onApply.run();
                        }
                    },
                    playerTweaksData.getScreenOffTimeoutSec() == timeoutSec));
        }

        for (int min : Helpers.range(30, 180, 30)) {
            int timeoutSec = min * 60;
            options.add(UiOptionItem.from(
                    context.getString(R.string.screen_dimming_timeout_min, min),
                    option -> {
                        playerTweaksData.setScreenOffTimeoutSec(timeoutSec);
                        if (onApply != null) {
                            onApply.run();
                        }
                    },
                    playerTweaksData.getScreenOffTimeoutSec() == timeoutSec));
        }

        String title = context.getString(R.string.player_screen_off_timeout);

        return OptionCategory.from(PLAYER_SCREEN_TIMEOUT_ID, OptionCategory.TYPE_RADIO_LIST, title, options);
    }

    public static OptionItem createExcludeFromContentBlockButton(
            Context context,  Video video, MediaServiceManager serviceManager, Runnable onClose) {
        return UiOptionItem.from(
                context.getString(
                        ContentBlockData.instance(context).isChannelExcluded(video.channelId) ?
                                R.string.content_block_stop_excluding_channel :
                                R.string.content_block_exclude_channel),
                optionItem -> {
                    if (video.hasChannel()) {
                        ContentBlockData.instance(context).toggleExcludeChannel(video.channelId);
                        if (onClose != null) {
                            onClose.run();
                        }
                    } else {
                        MessageHelpers.showMessage(context, R.string.wait_data_loading);

                        serviceManager.loadMetadata(
                                video,
                                metadata -> {
                                    video.sync(metadata);
                                    ContentBlockData.instance(context).excludeChannel(video.channelId);
                                    if (onClose != null) {
                                        onClose.run();
                                    }
                                }
                        );
                    }
                });
    }

    public static OptionCategory createSpeedListCategory(Context context, PlayerManager playbackController) {
        PlayerData playerData = PlayerData.instance(context);
        List<OptionItem> items = new ArrayList<>();

        PlayerTweaksData data = PlayerTweaksData.instance(context);
        for (float speed : data.isLongSpeedListEnabled() ? Utils.SPEED_LIST_LONG :
                data.isExtraLongSpeedListEnabled() ? Utils.SPEED_LIST_EXTRA_LONG : Utils.SPEED_LIST_SHORT) {
            items.add(UiOptionItem.from(
                    String.valueOf(speed),
                    optionItem -> {
                        if (playbackController != null) {
                            //playerData.setSpeed(playbackController.getVideo().channelId, speed);
                            playbackController.setSpeed(speed);
                        } else {
                            playerData.setSpeed(speed);
                        }
                    },
                    (playbackController != null ? playbackController.getSpeed() : playerData.getSpeed()) == speed));
        }

        return OptionCategory.from(PLAYER_SPEED_LIST_ID, OptionCategory.TYPE_RADIO_LIST, context.getString(R.string.video_speed), items);
    }

    public static OptionCategory createRememberSpeedCategory(Context context) {
        PlayerData playerData = PlayerData.instance(context);
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(context.getString(R.string.player_remember_speed_none),
                optionItem -> {
                    playerData.enableAllSpeed(false);
                    playerData.enableSpeedPerVideo(false);
                    playerData.enableSpeedPerChannel(false);
                },
                !playerData.isAllSpeedEnabled() && !playerData.isSpeedPerVideoEnabled()));

        options.add(UiOptionItem.from(context.getString(R.string.player_remember_speed_all),
                optionItem -> playerData.enableAllSpeed(true),
                playerData.isAllSpeedEnabled()));

        options.add(UiOptionItem.from(context.getString(R.string.player_remember_speed_each),
                optionItem -> playerData.enableSpeedPerVideo(true),
                playerData.isSpeedPerVideoEnabled()));

        options.add(UiOptionItem.from(context.getString(R.string.player_speed_per_channel),
                option -> playerData.enableSpeedPerChannel(option.isSelected()),
                playerData.isSpeedPerChannelEnabled()));

        String title = context.getString(R.string.player_remember_speed);

        return OptionCategory.from(PLAYER_REMEMBER_SPEED_ID, OptionCategory.TYPE_RADIO_LIST, title, options);
    }

    public static OptionCategory createSpeedMiscCategory(Context context) {
        PlayerTweaksData playerTweaksData = PlayerTweaksData.instance(context);
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(context.getString(R.string.player_long_speed_list),
                option -> playerTweaksData.enableLongSpeedList(option.isSelected()),
                playerTweaksData.isLongSpeedListEnabled()));

        options.add(UiOptionItem.from(context.getString(R.string.player_extra_long_speed_list),
                option -> playerTweaksData.enableExtraLongSpeedList(option.isSelected()),
                playerTweaksData.isExtraLongSpeedListEnabled()));

        String title = context.getString(R.string.player_other);

        return OptionCategory.from(PLAYER_SPEED_MISC_ID, OptionCategory.TYPE_CHECKBOX_LIST, title, options);
    }

    public static OptionCategory createPlaybackModeCategory(Context context) {
        return createPlaybackModeCategory(context, () -> {});
    }

    public static OptionCategory createPlaybackModeCategory(Context context, Runnable onModeSelected) {
        PlayerData playerData = PlayerData.instance(context);
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.repeat_mode_all, PlayerEngineConstants.PLAYBACK_MODE_ALL},
                {R.string.repeat_mode_one, PlayerEngineConstants.PLAYBACK_MODE_ONE},
                {R.string.repeat_mode_shuffle, PlayerEngineConstants.PLAYBACK_MODE_SHUFFLE},
                {R.string.repeat_mode_pause_alt, PlayerEngineConstants.PLAYBACK_MODE_LIST},
                {R.string.repeat_mode_reverse_list, PlayerEngineConstants.PLAYBACK_MODE_REVERSE_LIST},
                {R.string.repeat_mode_pause, PlayerEngineConstants.PLAYBACK_MODE_PAUSE},
                {R.string.repeat_mode_none, PlayerEngineConstants.PLAYBACK_MODE_CLOSE}
        }) {
            options.add(UiOptionItem.from(context.getString(pair[0]),
                    optionItem -> {
                        playerData.setRepeatMode(pair[1]);
                        onModeSelected.run();
                    },
                    playerData.getRepeatMode() == pair[1]
            ));
        }

        return OptionCategory.from(
                PLAYER_REPEAT_ID,
                OptionCategory.TYPE_RADIO_LIST,
                context.getString(R.string.action_repeat_mode),
                options
        );
    }

    public static OptionCategory createNetworkEngineCategory(Context context) {
        return createNetworkEngineCategory(context, () -> {});
    }

    public static OptionCategory createNetworkEngineCategory(Context context, Runnable onModeSelected) {
        PlayerTweaksData playerTweaksData = PlayerTweaksData.instance(context);
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(context.getString(R.string.default_lang),
                context.getString(R.string.default_stack_desc),
                option -> {
                    playerTweaksData.setPlayerDataSource(PlayerTweaksData.PLAYER_DATA_SOURCE_DEFAULT);
                    onModeSelected.run();
                },
                playerTweaksData.getPlayerDataSource() == PlayerTweaksData.PLAYER_DATA_SOURCE_DEFAULT));

        options.add(UiOptionItem.from("Cronet",
                context.getString(R.string.cronet_desc),
                option -> {
                    playerTweaksData.setPlayerDataSource(PlayerTweaksData.PLAYER_DATA_SOURCE_CRONET);
                    onModeSelected.run();
                },
                playerTweaksData.getPlayerDataSource() == PlayerTweaksData.PLAYER_DATA_SOURCE_CRONET));

        options.add(UiOptionItem.from("OkHttp",
                context.getString(R.string.okhttp_desc),
                option -> {
                    playerTweaksData.setPlayerDataSource(PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP);
                    onModeSelected.run();
                },
                playerTweaksData.getPlayerDataSource() == PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP));

        return OptionCategory.from(
                PLAYER_ENGINE_ID,
                OptionCategory.TYPE_RADIO_LIST,
                context.getString(R.string.player_network_stack),
                options
        );
    }

    public static OptionItem createSubscriptionsBackupButton(Context context) {
        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(context);
        List<OptionItem> options = new ArrayList<>();

        // Import from the file
        String filePickerTitle = context.getString(R.string.import_subscriptions_group) + " (GrayJay/PocketTube/NewPipe)";
        OptionItem item = UiOptionItem.from(filePickerTitle, optionItem -> {
            dialogPresenter.closeDialog();

            MotherActivity activity = getMotherActivity(context);

            if (PermissionHelpers.hasStoragePermissions(activity)) {
                runFilePicker(activity, filePickerTitle);
            } else {
                activity.addOnPermissions((requestCode, permissions, grantResults) -> {
                    if (requestCode == PermissionHelpers.REQUEST_EXTERNAL_STORAGE) {
                        if (grantResults.length >= 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                            runFilePicker(activity, filePickerTitle);
                        }
                    }
                });
                PermissionHelpers.verifyStoragePermissions(activity);
            }
        });

        return item;
    }

    private static void runFilePicker(Activity activity, String title) {
        new MaterialFilePicker()
                .withActivity(activity)
                .withTitle(title)
                .withRootPath(FileUtils.getFile(activity, null).getAbsolutePath())
                .start(FILE_PICKER_REQUEST_CODE);
    }

    @NonNull
    private static MotherActivity getMotherActivity(Context context) {
        ChannelGroupServiceWrapper mService = ChannelGroupServiceWrapper.instance(context);
        MotherActivity activity = (MotherActivity) context;
        activity.addOnResult((requestCode, resultCode, data) -> {
            if (FILE_PICKER_REQUEST_CODE == requestCode && resultCode == Activity.RESULT_OK) {
                String filePath = data.getStringExtra(FilePickerActivity.RESULT_FILE_PATH);
                RxHelper.execute(mService.importGroupsObserve(new File(filePath)), result -> pinGroups(context, result),
                        error -> MessageHelpers.showLongMessage(context, error.getMessage()));
            }
        });
        return activity;
    }

    private static void pinGroups(Context context, @NonNull List<ItemGroup> newGroups) {
        if (newGroups.isEmpty()) {
            // Already added to Subscriptions section
            MessageHelpers.showMessage(context, context.getString(R.string.msg_done));
            return;
        }

        for (ItemGroup group : newGroups) {
            BrowsePresenter.instance(context).pinItem(Video.from(group));
        }
        MessageHelpers.showMessage(context, context.getString(R.string.pinned_to_sidebar));
    }

    public static void showConfirmationDialog(Context context, String title, Runnable onConfirm) {
        showConfirmationDialog(context, title, onConfirm, () -> {});
    }

    public static void showConfirmationDialog(Context context, String title, Runnable onConfirm, Runnable onCancel) {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(context);

        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(context.getString(R.string.btn_confirm),
                option -> {
                    settingsPresenter.goBack();
                    onConfirm.run();
                }));

        options.add(UiOptionItem.from(context.getString(R.string.cancel),
                option -> {
                    settingsPresenter.goBack();
                    onCancel.run();
                }));

        settingsPresenter.appendStringsCategory(title, options);

        settingsPresenter.showDialog(title);
    }

    public static void appendSeekIntervalDialogItems(Context context, AppDialogPresenter dialogPresenter, PlayerData playerData, boolean closeOnSelect) {
        List<OptionItem> options = new ArrayList<>();

        for (int intervalMs : new int[] {1_000, 2_000, 3_000, 5_000, 7_000, 10_000, 15_000, 20_000, 30_000, 60_000}) {
            options.add(UiOptionItem.from(context.getString(R.string.seek_interval_sec, Helpers.toString(intervalMs / 1_000f)),
                    optionItem -> {
                        playerData.setStartSeekIncrementMs(intervalMs);
                        if (playerData.getSeekPreviewMode() == PlayerData.SEEK_PREVIEW_CAROUSEL_SLOW) {
                            Utils.showNotCompatibleMessage(context, R.string.player_seek_preview_carousel_slow);
                        }
                        if (closeOnSelect) {
                            dialogPresenter.closeDialog();
                        }
                    },
                    intervalMs == playerData.getStartSeekIncrementMs()));
        }

        dialogPresenter.appendRadioCategory(context.getString(R.string.seek_interval), options);
    }

    public static void showAddToPlaylistDialog(Context context, Video video, VideoMenuCallback callback) {
        //if (!YouTubeSignInService.instance().isSigned()) {
        //    MessageHelpers.showMessage(context, R.string.msg_signed_users_only);
        //    return;
        //}

        if (video == null) {
            return;
        }

        MediaItemService itemManager = YouTubeMediaItemService.instance();

        Disposable playlistsInfoAction = itemManager.getPlaylistsInfoObserve(video.videoId)
                .subscribe(
                        videoPlaylistInfos -> showAddToPlaylistDialog(context, video, callback, videoPlaylistInfos, null),
                        error -> {
                            // Fallback to something on error
                            Log.e(TAG, "Get playlists error: %s", error.getMessage());
                        }
                );
    }

    public static void showAddToPlaylistDialog(Context context, Video video, VideoMenuCallback callback, List<PlaylistInfo> playlistInfos, Runnable onFinish) {
        if (playlistInfos == null) {
            MessageHelpers.showMessage(context, R.string.msg_signed_users_only);
            return;
        }

        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(context);

        appendPlaylistDialogContent(context, video, callback, dialogPresenter, playlistInfos);
        dialogPresenter.showDialog(context.getString(R.string.dialog_add_to_playlist), () -> {
            if (onFinish != null) onFinish.run();
        });
    }

    private static void appendPlaylistDialogContent(
            Context context, Video video, VideoMenuCallback callback, AppDialogPresenter dialogPresenter, List<PlaylistInfo> playlistInfos) {
        List<OptionItem> options = new ArrayList<>();

        for (PlaylistInfo playlistInfo : playlistInfos) {
            options.add(UiOptionItem.from(
                    playlistInfo.getTitle(),
                    (item) -> {
                        if (playlistInfo instanceof YouTubePlaylistInfo) {
                            ((YouTubePlaylistInfo) playlistInfo).setSelected(item.isSelected());
                        }
                        addRemoveFromPlaylist(context, video, callback, playlistInfo.getPlaylistId(), item.isSelected());
                        GeneralData.instance(context).setLastPlaylistId(playlistInfo.getPlaylistId());
                        GeneralData.instance(context).setLastPlaylistTitle(playlistInfo.getTitle());
                    },
                    playlistInfo.isSelected()));
        }

        dialogPresenter.appendCheckedCategory(context.getString(R.string.dialog_add_to_playlist), options);
    }

    private static void addRemoveFromPlaylist(Context context, Video video, VideoMenuCallback callback, String playlistId, boolean add) {
        if (video == null) {
            return;
        }

        Observable<Void> editObserve;
        MediaItemService itemManager = YouTubeMediaItemService.instance();

        if (add) {
            editObserve = video.mediaItem != null ?
                    itemManager.addToPlaylistObserve(playlistId, video.mediaItem) : itemManager.addToPlaylistObserve(playlistId, video.videoId);
        } else {
            // Check that the current video belongs to the right section
            if (callback != null && Helpers.equals(video.playlistId, playlistId)) {
                callback.onItemAction(video, VideoMenuCallback.ACTION_REMOVE_FROM_PLAYLIST);
            }
            editObserve = itemManager.removeFromPlaylistObserve(playlistId, video.videoId);
        }

        // Handle error: Maximum playlist size exceeded (> 5000 items)
        RxHelper.execute(editObserve, error -> MessageHelpers.showLongMessage(context, error.getMessage()));
    }

    public static void showPlaylistOrderDialog(Context context, Video video, Runnable onClose) {
        if (video == null) {
            return;
        }

        if (video.hasPlaylist()) {
            showPlaylistOrderDialog(context, video.playlistId, onClose);
        } else if (video.belongsToUserPlaylists()) {
            MediaServiceManager.instance().loadChannelUploads(video, group -> {
                if (group.getMediaItems() == null || group.getMediaItems().isEmpty()) {
                    return;
                }

                MediaItem first = group.getMediaItems().get(0);
                String playlistId = first.getPlaylistId();

                showPlaylistOrderDialog(context, playlistId, onClose);
            });
        }
    }

    public static void showPlaylistOrderDialog(Context context, String playlistId, Runnable onClose) {
        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(context);

        GeneralData generalData = GeneralData.instance(context);

        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.playlist_order_added_date_newer_first, MediaItemService.PLAYLIST_ORDER_ADDED_DATE_NEWER_FIRST},
                {R.string.playlist_order_added_date_older_first, MediaItemService.PLAYLIST_ORDER_ADDED_DATE_OLDER_FIRST},
                {R.string.playlist_order_popularity, MediaItemService.PLAYLIST_ORDER_POPULARITY},
                {R.string.playlist_order_published_date_newer_first, MediaItemService.PLAYLIST_ORDER_PUBLISHED_DATE_NEWER_FIRST},
                {R.string.playlist_order_published_date_older_first, MediaItemService.PLAYLIST_ORDER_PUBLISHED_DATE_OLDER_FIRST}
        }) {
            options.add(UiOptionItem.from(context.getString(pair[0]), optionItem -> {
                if (optionItem.isSelected()) {
                    RxHelper.execute(
                            YouTubeMediaItemService.instance().setPlaylistOrderObserve(playlistId, pair[1]),
                            (error) -> MessageHelpers.showMessage(context, R.string.owned_playlist_warning),
                            () -> {
                                generalData.setPlaylistOrder(playlistId, pair[1]);
                                ViewManager.instance(context).refreshCurrentView();
                                if (onClose != null) {
                                    dialogPresenter.closeDialog();
                                    onClose.run();
                                }
                                MessageHelpers.showMessage(context, R.string.msg_done);
                            }
                    );
                }
            }, generalData.getPlaylistOrder(playlistId) == pair[1]));
        }

        dialogPresenter.appendRadioCategory(context.getString(R.string.playlist_order), options);

        dialogPresenter.showDialog(context.getString(R.string.playlist_order));
    }

    public interface OnVideoClick {
        void onClick(Video item);
    }

    public static void showPlaybackQueueDialog(Context context, OnVideoClick onClick) {
        String playbackQueueCategoryTitle = context.getString(R.string.playback_queue_category_title);

        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(context);

        List<OptionItem> options = new ArrayList<>();

        Playlist playlist = Playlist.instance();

        for (Video video : playlist.getAll()) {
            String title = video.getTitle();
            String author = video.getAuthor();
            //options.add(0, UiOptionItem.from( // Add to start (recent videos on top)
            options.add(UiOptionItem.from( // Add to end (like on mobile client)
                    String.format("%s - %s", title != null ? title : "...", author != null ? author : "..."),
                    optionItem -> {
                        video.fromQueue = true;
                        onClick.onClick(video);
                        settingsPresenter.closeDialog();
                    },
                    video == playlist.getCurrent())
            );
        }

        settingsPresenter.appendRadioCategory(playbackQueueCategoryTitle, options);

        settingsPresenter.showDialog(playbackQueueCategoryTitle);
    }
}
