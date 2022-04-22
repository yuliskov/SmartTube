package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.os.Build;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackEngineController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem.VideoPreset;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleManager.OnSelectSubtitleStyle;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleManager.SubtitleStyle;
import com.liskovsoft.smartyoutubetv2.common.misc.AppDataSourceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class AppDialogUtil {
    private static final int VIDEO_BUFFER_ID = 134;
    private static final int BACKGROUND_PLAYBACK_ID = 135;
    private static final int VIDEO_PRESETS_ID = 136;
    private static final int AUDIO_DELAY_ID = 137;
    private static final int SUBTITLE_STYLES_ID = 45;

    /**
     * Adds share link items to existing dialog.
     */
    public static void appendShareDialogItems(Context context, AppDialogPresenter dialogPresenter, Video video) {
        if (video == null) {
            return;
        }

        if (video.videoId == null && video.channelId == null) {
            return;
        }

        dialogPresenter.appendSingleButton(
                UiOptionItem.from(context.getString(R.string.share_link), optionItem -> {
                    if (video.videoId != null) {
                        Utils.displayShareVideoDialog(context, video.videoId);
                    } else if (video.channelId != null) {
                        Utils.displayShareChannelDialog(context, video.channelId);
                    }
                }));

        dialogPresenter.appendSingleButton(
                UiOptionItem.from(context.getString(R.string.share_embed_link), optionItem -> {
                    if (video.videoId != null) {
                        Utils.displayShareEmbedVideoDialog(context, video.videoId);
                    }
                }));
    }

    public static OptionCategory createBackgroundPlaybackCategory(Context context, PlayerData playerData) {
        return createBackgroundPlaybackCategory(context, playerData, () -> {});
    }

    public static OptionCategory createBackgroundPlaybackCategory(Context context, PlayerData playerData, Runnable onSetCallback) {
        String categoryTitle = context.getString(R.string.category_background_playback);

        List<OptionItem> options = new ArrayList<>();
        options.add(UiOptionItem.from(context.getString(R.string.option_background_playback_off),
                optionItem -> {
                    playerData.setBackgroundMode(PlaybackEngineController.BACKGROUND_MODE_DEFAULT);
                    onSetCallback.run();
                }, playerData.getBackgroundMode() == PlaybackEngineController.BACKGROUND_MODE_DEFAULT));
        if (Helpers.isAndroidTV(context) && Build.VERSION.SDK_INT < 26) { // useful only for pre-Oreo UI
            options.add(UiOptionItem.from(context.getString(R.string.option_background_playback_behind) + " (Android TV 5,6,7)",
                    optionItem -> {
                        playerData.setBackgroundMode(PlaybackEngineController.BACKGROUND_MODE_PLAY_BEHIND);
                        onSetCallback.run();
                    }, playerData.getBackgroundMode() == PlaybackEngineController.BACKGROUND_MODE_PLAY_BEHIND));
        }
        if (Helpers.isPictureInPictureSupported(context)) {
            options.add(UiOptionItem.from(context.getString(R.string.option_background_playback_pip),
                    optionItem -> {
                        playerData.setBackgroundMode(PlaybackEngineController.BACKGROUND_MODE_PIP);
                        onSetCallback.run();
                    }, playerData.getBackgroundMode() == PlaybackEngineController.BACKGROUND_MODE_PIP));
        }
        options.add(UiOptionItem.from(context.getString(R.string.option_background_playback_only_audio),
                optionItem -> {
                    playerData.setBackgroundMode(PlaybackEngineController.BACKGROUND_MODE_SOUND);
                    onSetCallback.run();
                }, playerData.getBackgroundMode() == PlaybackEngineController.BACKGROUND_MODE_SOUND));

        return OptionCategory.from(BACKGROUND_PLAYBACK_ID, OptionCategory.TYPE_RADIO, categoryTitle, options);
    }

    public static OptionCategory createVideoPresetsCategory(Context context, PlayerData playerData) {
        return createVideoPresetsCategory(context, playerData, () -> {});
    }

    public static OptionCategory createVideoPresetsCategory(Context context, PlayerData playerData, Runnable onFormatSelected) {
        return OptionCategory.from(
                VIDEO_PRESETS_ID,
                OptionCategory.TYPE_RADIO,
                context.getString(R.string.title_video_presets),
                fromPresets(
                        context,
                        AppDataSourceManager.instance().getVideoPresets(),
                        playerData,
                        onFormatSelected
                )
        );
    }

    private static List<OptionItem> fromPresets(Context context, VideoPreset[] presets, PlayerData playerData, Runnable onFormatSelected) {
        List<OptionItem> result = new ArrayList<>();

        FormatItem selectedFormat = playerData.getFormat(FormatItem.TYPE_VIDEO);
        boolean isPresetSelection = selectedFormat != null && selectedFormat.isPreset();

        for (VideoPreset preset : presets) {
            result.add(0, UiOptionItem.from(preset.name,
                    option -> setFormat(preset.format, playerData, onFormatSelected),
                    isPresetSelection && preset.format.equals(selectedFormat)));
        }

        result.add(0, UiOptionItem.from(
                context.getString(R.string.video_preset_disabled),
                optionItem -> setFormat(playerData.getDefaultVideoFormat(), playerData, onFormatSelected),
                !isPresetSelection));

        return result;
    }

    public static OptionCategory createVideoBufferCategory(Context context, PlayerData playerData) {
        return createVideoBufferCategory(context, playerData, () -> {});
    }

    private static void setFormat(FormatItem formatItem, PlayerData playerData, Runnable onFormatSelected) {
        if (playerData.isLegacyCodecsForced()) {
            playerData.forceLegacyCodecs(false);
        }
        playerData.setFormat(formatItem);
        onFormatSelected.run();
    }

    public static OptionCategory createVideoBufferCategory(Context context, PlayerData playerData, Runnable onBufferSelected) {
        String videoBufferTitle = context.getString(R.string.video_buffer);
        List<OptionItem> optionItems = new ArrayList<>();
        optionItems.add(createVideoBufferOption(context, playerData, R.string.video_buffer_size_low, PlaybackEngineController.BUFFER_LOW, onBufferSelected));
        optionItems.add(createVideoBufferOption(context, playerData, R.string.video_buffer_size_med, PlaybackEngineController.BUFFER_MED, onBufferSelected));
        optionItems.add(createVideoBufferOption(context, playerData, R.string.video_buffer_size_high, PlaybackEngineController.BUFFER_HIGH, onBufferSelected));
        return OptionCategory.from(VIDEO_BUFFER_ID, OptionCategory.TYPE_RADIO, videoBufferTitle, optionItems);
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

    public static OptionCategory createAudioShiftCategory(Context context, PlayerData playerData) {
        return createAudioShiftCategory(context, playerData, () -> {});
    }

    public static OptionCategory createAudioShiftCategory(Context context, PlayerData playerData, Runnable onSetCallback) {
        String title = context.getString(R.string.audio_shift);

        List<OptionItem> options = new ArrayList<>();

        for (int delayMs : Helpers.range(-4_000, 4_000, 50)) {
            options.add(UiOptionItem.from(context.getString(R.string.audio_shift_sec, Helpers.toString(delayMs / 1_000f)),
                    optionItem -> {
                        playerData.setAudioDelayMs(delayMs);
                        onSetCallback.run();
                    },
                    delayMs == playerData.getAudioDelayMs()));
        }

        return OptionCategory.from(AUDIO_DELAY_ID, OptionCategory.TYPE_RADIO, title, options);
    }

    public static OptionCategory createSubtitleStylesCategory(Context context, PlayerData playerData) {
        return createSubtitleStylesCategory(context, playerData, style -> {});
    }

    private static OptionCategory createSubtitleStylesCategory(Context context, PlayerData playerData, OnSelectSubtitleStyle onSelectSubtitleStyle) {
        List<SubtitleStyle> subtitleStyles = playerData.getSubtitleStyles();

        String subtitleStyleTitle = context.getString(R.string.subtitle_style);

        return OptionCategory.from(SUBTITLE_STYLES_ID, OptionCategory.TYPE_RADIO, subtitleStyleTitle, fromSubtitleStyles(context, playerData, subtitleStyles, onSelectSubtitleStyle));
    }

    private static List<OptionItem> fromSubtitleStyles(Context context, PlayerData playerData, List<SubtitleStyle> subtitleStyles, OnSelectSubtitleStyle onSelectSubtitleStyle) {
        List<OptionItem> styleOptions = new ArrayList<>();

        for (SubtitleStyle subtitleStyle : subtitleStyles) {
            styleOptions.add(UiOptionItem.from(
                    context.getString(subtitleStyle.nameResId),
                    option -> {
                        playerData.setSubtitleStyle(subtitleStyle);
                        onSelectSubtitleStyle.onSelectSubtitleStyle(subtitleStyle);
                    },
                    subtitleStyle.equals(playerData.getSubtitleStyle())));
        }

        return styleOptions;
    }

    public static OptionCategory createVideoZoomCategory(Context context, PlayerData playerData) {
        return createVideoZoomCategory(context, playerData, () -> {});
    }

    public static OptionCategory createVideoZoomCategory(Context context, PlayerData playerData, Runnable onSelectZoomMode) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.video_zoom_default, PlaybackEngineController.ZOOM_MODE_DEFAULT},
                {R.string.video_zoom_fit_width, PlaybackEngineController.ZOOM_MODE_FIT_WIDTH},
                {R.string.video_zoom_fit_height, PlaybackEngineController.ZOOM_MODE_FIT_HEIGHT},
                {R.string.video_zoom_fit_both, PlaybackEngineController.ZOOM_MODE_FIT_BOTH},
                {R.string.video_zoom_stretch, PlaybackEngineController.ZOOM_MODE_STRETCH}}) {
            options.add(UiOptionItem.from(context.getString(pair[0]),
                    optionItem -> {
                        playerData.setVideoZoomMode(pair[1]);
                        onSelectZoomMode.run();
                    },
                    playerData.getVideoZoomMode() == pair[1]));
        }

        String videoZoomTitle = context.getString(R.string.video_zoom);

        return OptionCategory.from(SUBTITLE_STYLES_ID, OptionCategory.TYPE_RADIO, videoZoomTitle, options);
    }

    public static OptionCategory createVideoAspectCategory(Context context, PlayerData playerData, Runnable onSelectAspectMode) {
        List<OptionItem> options = new ArrayList<>();

        Map<String, Float> pairs = new LinkedHashMap<>();
        pairs.put(context.getString(R.string.video_zoom_default), PlaybackEngineController.ASPECT_RATIO_DEFAULT);
        pairs.put("1:1", PlaybackEngineController.ASPECT_RATIO_1_1);
        pairs.put("4:3", PlaybackEngineController.ASPECT_RATIO_4_3);
        pairs.put("5:4", PlaybackEngineController.ASPECT_RATIO_5_4);
        pairs.put("16:9", PlaybackEngineController.ASPECT_RATIO_16_9);
        pairs.put("16:10", PlaybackEngineController.ASPECT_RATIO_16_10);
        pairs.put("21:9 (2.33:1)", PlaybackEngineController.ASPECT_RATIO_21_9);
        pairs.put("64:27 (2.37:1)", PlaybackEngineController.ASPECT_RATIO_64_27);
        pairs.put("2.21:1", PlaybackEngineController.ASPECT_RATIO_221_1);
        pairs.put("2.35:1", PlaybackEngineController.ASPECT_RATIO_235_1);
        pairs.put("2.39:1", PlaybackEngineController.ASPECT_RATIO_239_1);

        for (Entry<String, Float> entry: pairs.entrySet()) {
            options.add(UiOptionItem.from(entry.getKey(),
                    optionItem -> {
                        playerData.setVideoAspectRatio(entry.getValue());
                        onSelectAspectMode.run();
                    }, Helpers.floatEquals(playerData.getVideoAspectRatio(), entry.getValue())));
        }

        String videoZoomTitle = context.getString(R.string.video_aspect);

        return OptionCategory.from(SUBTITLE_STYLES_ID, OptionCategory.TYPE_RADIO, videoZoomTitle, options);
    }

    public static void showConfirmationDialog(Context context, Runnable onConfirm, String title) {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(context);
        settingsPresenter.clear();

        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(context.getString(R.string.cancel),
                option -> settingsPresenter.goBack()));

        options.add(UiOptionItem.from(context.getString(R.string.btn_confirm),
                option -> {
                    settingsPresenter.goBack();
                    onConfirm.run();
                }));

        settingsPresenter.appendStringsCategory(title, options);

        settingsPresenter.showDialog(title);
    }
}
