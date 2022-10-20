package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.os.Build;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.PlaylistInfo;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackEngineController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter.VideoMenuCallback;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleManager.SubtitleStyle;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem.VideoPreset;
import com.liskovsoft.smartyoutubetv2.common.misc.AppDataSourceManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.youtubeapi.service.YouTubeMediaItemService;
import com.liskovsoft.youtubeapi.service.YouTubeSignInService;
import com.liskovsoft.youtubeapi.service.data.YouTubePlaylistInfo;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

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
    private static final int SUBTITLE_SIZE_ID = 46;
    private static final int SUBTITLE_POSITION_ID = 47;
    private static final String TAG = AppDialogUtil.class.getSimpleName();

    /**
     * Adds share link items to existing dialog.
     */
    public static void appendShareLinkDialogItem(Context context, AppDialogPresenter dialogPresenter, Video video) {
        if (video == null) {
            return;
        }

        if (video.videoId == null && video.channelId == null) {
            return;
        }

        dialogPresenter.appendSingleButton(
                UiOptionItem.from(context.getString(R.string.share_link), optionItem -> {
                    if (video.videoId != null) {
                        Utils.displayShareVideoDialog(context, video.videoId, (int)(video.getPositionMs() / 1_000));
                    } else if (video.channelId != null) {
                        Utils.displayShareChannelDialog(context, video.channelId);
                    }
                }));
    }

    /**
     * Adds share link items to existing dialog.
     */
    public static void appendShareEmbedLinkDialogItem(Context context, AppDialogPresenter dialogPresenter, Video video) {
        if (video == null) {
            return;
        }

        if (video.videoId == null && video.channelId == null) {
            return;
        }

        dialogPresenter.appendSingleButton(
                UiOptionItem.from(context.getString(R.string.share_embed_link), optionItem -> {
                    if (video.videoId != null) {
                        Utils.displayShareEmbedVideoDialog(context, video.videoId);
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
                    playerData.setBackgroundMode(PlaybackEngineController.BACKGROUND_MODE_DEFAULT);
                    generalData.setBackgroundPlaybackShortcut(GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME);
                    onSetCallback.run();
                }, playerData.getBackgroundMode() == PlaybackEngineController.BACKGROUND_MODE_DEFAULT));

        if (Helpers.isPictureInPictureSupported(context)) {
            String pip = context.getString(R.string.option_background_playback_pip);
            options.add(UiOptionItem.from(String.format("%s (%s)", pip, context.getString(R.string.pressing_home)),
                    optionItem -> {
                        playerData.setBackgroundMode(PlaybackEngineController.BACKGROUND_MODE_PIP);
                        generalData.setBackgroundPlaybackShortcut(GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME);
                        onSetCallback.run();
                    }, playerData.getBackgroundMode() == PlaybackEngineController.BACKGROUND_MODE_PIP &&
                    generalData.getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME));

            options.add(UiOptionItem.from(String.format("%s (%s)", pip, context.getString(R.string.pressing_home_back)),
                    optionItem -> {
                        playerData.setBackgroundMode(PlaybackEngineController.BACKGROUND_MODE_PIP);
                        generalData.setBackgroundPlaybackShortcut(GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME_BACK);
                        onSetCallback.run();
                    }, playerData.getBackgroundMode() == PlaybackEngineController.BACKGROUND_MODE_PIP &&
                    generalData.getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME_BACK));
        }

        String audio = context.getString(R.string.option_background_playback_only_audio);
        options.add(UiOptionItem.from(String.format("%s (%s)", audio, context.getString(R.string.pressing_home)),
                optionItem -> {
                    playerData.setBackgroundMode(PlaybackEngineController.BACKGROUND_MODE_SOUND);
                    generalData.setBackgroundPlaybackShortcut(GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME);
                    onSetCallback.run();
                }, playerData.getBackgroundMode() == PlaybackEngineController.BACKGROUND_MODE_SOUND &&
                    generalData.getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME));
        options.add(UiOptionItem.from(String.format("%s (%s)", audio, context.getString(R.string.pressing_home_back)),
                optionItem -> {
                    playerData.setBackgroundMode(PlaybackEngineController.BACKGROUND_MODE_SOUND);
                    generalData.setBackgroundPlaybackShortcut(GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME_BACK);
                    onSetCallback.run();
                }, playerData.getBackgroundMode() == PlaybackEngineController.BACKGROUND_MODE_SOUND &&
                    generalData.getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME_BACK));

        if (Helpers.isAndroidTV(context) && Build.VERSION.SDK_INT < 26) { // useful only for pre-Oreo UI
            String behind = context.getString(R.string.option_background_playback_behind);
            options.add(UiOptionItem.from(String.format("%s (%s - %s)", behind, "Android TV 5,6,7", context.getString(R.string.pressing_home)),
                    optionItem -> {
                        playerData.setBackgroundMode(PlaybackEngineController.BACKGROUND_MODE_PLAY_BEHIND);
                        generalData.setBackgroundPlaybackShortcut(GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME);
                        onSetCallback.run();
                    }, playerData.getBackgroundMode() == PlaybackEngineController.BACKGROUND_MODE_PLAY_BEHIND &&
                        generalData.getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME));
            options.add(UiOptionItem.from(String.format("%s (%s - %s)", behind, "Android TV 5,6,7", context.getString(R.string.pressing_home_back)),
                    optionItem -> {
                        playerData.setBackgroundMode(PlaybackEngineController.BACKGROUND_MODE_PLAY_BEHIND);
                        generalData.setBackgroundPlaybackShortcut(GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME_BACK);
                        onSetCallback.run();
                    }, playerData.getBackgroundMode() == PlaybackEngineController.BACKGROUND_MODE_PLAY_BEHIND &&
                        generalData.getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME_BACK));
        }

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
            if (preset.isVP9Preset() && !Helpers.isVP9ResolutionSupported(preset.getHeight())) {
                continue;
            }

            if (preset.isAV1Preset() && !Helpers.isAV1ResolutionSupported(preset.getHeight())) {
                continue;
            }

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
        optionItems.add(createVideoBufferOption(context, playerData, R.string.video_buffer_size_none, PlaybackEngineController.BUFFER_NONE, onBufferSelected));
        optionItems.add(createVideoBufferOption(context, playerData, R.string.video_buffer_size_low, PlaybackEngineController.BUFFER_LOW, onBufferSelected));
        optionItems.add(createVideoBufferOption(context, playerData, R.string.video_buffer_size_med, PlaybackEngineController.BUFFER_MEDIUM, onBufferSelected));
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
        List<SubtitleStyle> subtitleStyles = playerData.getSubtitleStyles();

        String subtitleStyleTitle = context.getString(R.string.subtitle_style);

        return OptionCategory.from(SUBTITLE_STYLES_ID, OptionCategory.TYPE_RADIO, subtitleStyleTitle, fromSubtitleStyles(context, playerData, subtitleStyles));
    }

    private static List<OptionItem> fromSubtitleStyles(Context context, PlayerData playerData, List<SubtitleStyle> subtitleStyles) {
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

    public static OptionCategory createSubtitleSizeCategory(Context context, PlayerData playerData) {
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

        return OptionCategory.from(SUBTITLE_SIZE_ID, OptionCategory.TYPE_RADIO, context.getString(R.string.subtitle_scale), options);
    }

    public static OptionCategory createSubtitlePositionCategory(Context context, PlayerData playerData) {
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

        return OptionCategory.from(SUBTITLE_POSITION_ID, OptionCategory.TYPE_RADIO, context.getString(R.string.subtitle_position), options);
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

        options.add(UiOptionItem.from(context.getString(R.string.btn_confirm),
                option -> {
                    settingsPresenter.goBack();
                    onConfirm.run();
                }));

        options.add(UiOptionItem.from(context.getString(R.string.cancel),
                option -> settingsPresenter.goBack()));

        settingsPresenter.appendStringsCategory(title, options);

        settingsPresenter.showDialog(title);
    }

    public static void appendSeekIntervalDialogItems(Context context, AppDialogPresenter dialogPresenter, PlayerData playerData, boolean closeOnSelect) {
        List<OptionItem> options = new ArrayList<>();

        for (int intervalMs : new int[] {1_000, 2_000, 3_000, 5_000, 10_000, 15_000, 20_000, 30_000, 60_000}) {
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

    public static void appendSpeedDialogItems(Context context, AppDialogPresenter settingsPresenter, PlayerData playerData, PlaybackController playbackController) {
        List<OptionItem> items = new ArrayList<>();
        float[] speedValues = new float[]{0.25f, 0.5f, 0.75f, 0.80f, 0.85f, 0.90f, 0.95f, 1.0f, 1.05f, 1.1f, 1.15f, 1.2f, 1.25f, 1.3f, 1.4f, 1.5f, 1.75f, 2f, 2.25f, 2.5f, 2.75f, 3.0f};

        for (float speed : speedValues) {
            items.add(UiOptionItem.from(
                    String.valueOf(speed),
                    optionItem -> {
                        playerData.setSpeed(speed);
                        playbackController.setSpeed(speed);
                        //settingsPresenter.closeDialog();
                    },
                    playbackController.getSpeed() == speed));
        }
        settingsPresenter.appendRadioCategory(context.getString(R.string.video_speed), items);
    }

    public static void showAddToPlaylistDialog(Context context, Video video, VideoMenuCallback callback) {
        if (!YouTubeSignInService.instance().isSigned()) {
            MessageHelpers.showMessage(context, R.string.msg_signed_users_only);
            return;
        }

        if (video == null) {
            return;
        }

        MediaItemService itemManager = YouTubeMediaItemService.instance();

        Disposable playlistsInfoAction = itemManager.getPlaylistsInfoObserve(video.videoId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
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
        dialogPresenter.clear();

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
        Observable<Void> editObserve;
        MediaItemService itemManager = YouTubeMediaItemService.instance();

        if (add) {
            editObserve = itemManager.addToPlaylistObserve(playlistId, video.videoId);
        } else {
            // Check that the current video belongs to the right section
            if (callback != null && Helpers.equals(video.playlistId, playlistId)) {
                callback.onItemAction(video, VideoMenuCallback.ACTION_REMOVE_FROM_PLAYLIST);
            }
            editObserve = itemManager.removeFromPlaylistObserve(playlistId, video.videoId);
        }

        Disposable addRemoveAction = RxUtils.execute(editObserve); // ignore results (do the work in the background)
    }

    public static void showPlaylistOrderDialog(Context context, Video video, Runnable onClose) {
        if (video == null) {
            return;
        }

        if (video.hasPlaylist()) {
            showPlaylistOrderDialog(context, video.playlistId, onClose);
        } else if (video.belongsToUserPlaylists()) {
            MediaServiceManager.instance().loadChannelUploads(video, group -> {
                MediaItem first = group.getMediaItems().get(0);
                String playlistId = first.getPlaylistId();

                showPlaylistOrderDialog(context, playlistId, onClose);
            });
        }
    }

    public static void showPlaylistOrderDialog(Context context, String playlistId, Runnable onClose) {
        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(context);
        dialogPresenter.clear();

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
                    RxUtils.execute(
                            YouTubeMediaItemService.instance().setPlaylistOrderObserve(playlistId, pair[1]),
                            () -> MessageHelpers.showMessage(context, R.string.owned_playlist_warning),
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

        settingsPresenter.clear();

        List<OptionItem> options = new ArrayList<>();

        Playlist playlist = Playlist.instance();

        for (Video video : playlist.getAll()) {
            String title = video.getTitle();
            String author = video.getAuthor();
            options.add(0, UiOptionItem.from( // Add to start (recent videos on top)
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
