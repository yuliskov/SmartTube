package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import android.content.Context;
import android.os.Build;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackEngineController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.AppDataSourceManager;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem.VideoPreset;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HQDialogManager extends PlayerEventListenerHelper {
    private static final String TAG = HQDialogManager.class.getSimpleName();
    private static final int VIDEO_FORMATS_ID = 132;
    private static final int AUDIO_FORMATS_ID = 133;
    private static final int VIDEO_BUFFER_ID = 134;
    private static final int BACKGROUND_PLAYBACK_ID = 135;
    private static final int VIDEO_PRESETS_ID = 136;
    private static final int AUDIO_DELAY_ID = 137;
    // NOTE: using map, because same item could be changed time to time
    private final Map<Integer, OptionCategory> mCategories = new LinkedHashMap<>();
    private final Map<Integer, OptionCategory> mCategoriesInt = new LinkedHashMap<>();
    private final Set<Runnable> mHideListeners = new HashSet<>();
    private final StateUpdater mStateUpdater;
    private PlayerData mPlayerData;
    private AppDialogPresenter mSettingsPresenter;;

    public HQDialogManager(StateUpdater stateUpdater) {
        mStateUpdater = stateUpdater;
    }

    @Override
    public void onInitDone() {
        mPlayerData = PlayerData.instance(getActivity());
        mSettingsPresenter = AppDialogPresenter.instance(getActivity());
    }

    @Override
    public void onViewResumed() {
        updateBackgroundPlayback();
    }

    @Override
    public void onHighQualityClicked() {
        mSettingsPresenter.clear();

        addQualityCategories();
        addVideoBufferCategory();
        //addPresetsCategory();
        addAudioDelayCategory();
        //addBackgroundPlaybackCategory();

        appendOptions(mCategoriesInt);
        appendOptions(mCategories);

        mSettingsPresenter.showDialog(getActivity().getString(R.string.playback_settings), this::onDialogHide);
    }

    private void addQualityCategories() {
        List<FormatItem> videoFormats = getController().getVideoFormats();
        String videoFormatsTitle = getActivity().getString(R.string.title_video_formats);

        List<FormatItem> audioFormats = getController().getAudioFormats();
        String audioFormatsTitle = getActivity().getString(R.string.title_audio_formats);

        addCategoryInt(OptionCategory.from(
                VIDEO_FORMATS_ID,
                OptionCategory.TYPE_RADIO,
                videoFormatsTitle,
                UiOptionItem.from(videoFormats, this::selectFormatOption)));
        addCategoryInt(OptionCategory.from(
                AUDIO_FORMATS_ID,
                OptionCategory.TYPE_RADIO,
                audioFormatsTitle,
                UiOptionItem.from(audioFormats, this::selectFormatOption)));
    }

    private void selectFormatOption(OptionItem option) {
        FormatItem formatItem = UiOptionItem.toFormat(option);
        getController().setFormat(formatItem);

        if (mPlayerData.getFormat(formatItem.getType()).isPreset()) {
            // Preset currently active. Show warning about format reset.
            MessageHelpers.showMessage(getActivity(), R.string.video_preset_enabled);
        }

        if (!getController().containsMedia()) {
            getController().reloadPlayback();
        }
    }

    private void addVideoBufferCategory() {
        addCategoryInt(createVideoBufferCategory(getActivity(), mPlayerData,
                () -> getController().restartEngine()));
    }

    private void addAudioDelayCategory() {
        addCategoryInt(createAudioShiftCategory(getActivity(), mPlayerData,
                () -> getController().restartEngine()));
    }

    private void onDialogHide() {
        updateBackgroundPlayback();

        for (Runnable listener : mHideListeners) {
            listener.run();
        }
    }

    private void updateBackgroundPlayback() {
        ViewManager.instance(getActivity()).blockTop(null);

        getController().setPlaybackMode(mPlayerData.getBackgroundMode());
    }

    private void addBackgroundPlaybackCategory() {
        OptionCategory category =
                createBackgroundPlaybackCategory(getActivity(), mPlayerData, this::updateBackgroundPlayback);

        addCategoryInt(category);
    }

    private void addPresetsCategory() {
        addCategoryInt(createVideoPresetsCategory(
                getActivity(), mPlayerData, () -> getController().setFormat(mPlayerData.getFormat(FormatItem.TYPE_VIDEO))));
    }

    private void removeCategoryInt(int id) {
        mCategoriesInt.remove(id);
    }

    private void addCategoryInt(OptionCategory category) {
        mCategoriesInt.put(category.id, category);
    }

    public void removeCategory(int id) {
        mCategories.remove(id);
    }

    public void addCategory(OptionCategory category) {
        mCategories.put(category.id, category);
    }

    public void addOnDialogHide(Runnable listener) {
        mHideListeners.add(listener);
    }

    public void removeOnDialogHide(Runnable listener) {
        mHideListeners.remove(listener);
    }

    private void appendOptions(Map<Integer, OptionCategory> categories) {
        for (OptionCategory category : categories.values()) {
            switch (category.type) {
                case OptionCategory.TYPE_RADIO:
                    mSettingsPresenter.appendRadioCategory(category.title, category.options);
                    break;
                case OptionCategory.TYPE_CHECKED:
                    mSettingsPresenter.appendCheckedCategory(category.title, category.options);
                    break;
                case OptionCategory.TYPE_SINGLE:
                    mSettingsPresenter.appendSingleSwitch(category.option);
                    break;
            }
        }
    }

    public static OptionCategory createBackgroundPlaybackCategory(Context context, PlayerData playerData) {
        return createBackgroundPlaybackCategory(context, playerData, () -> {});
    }

    private static OptionCategory createBackgroundPlaybackCategory(Context context, PlayerData playerData, Runnable onSetCallback) {
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

    private static OptionCategory createVideoPresetsCategory(Context context, PlayerData playerData, Runnable onFormatSelected) {
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
                optionItem -> setFormat(FormatItem.VIDEO_AUTO, playerData, onFormatSelected),
                !isPresetSelection));

        return result;
    }

    public static OptionCategory createVideoBufferCategory(Context context, PlayerData playerData) {
        return createVideoBufferCategory(context, playerData, () -> {});
    }

    private static void setFormat(FormatItem formatItem, PlayerData playerData, Runnable onFormatSelected) {
        if (playerData.isLowQualityEnabled()) {
            playerData.enableLowQuality(false);
        }
        playerData.setFormat(formatItem);
        onFormatSelected.run();
    }

    private static OptionCategory createVideoBufferCategory(Context context, PlayerData playerData, Runnable onBufferSelected) {
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

    private static OptionCategory createAudioShiftCategory(Context context, PlayerData playerData, Runnable onSetCallback) {
        String title = context.getString(R.string.audio_shift);

        List<OptionItem> options = new ArrayList<>();

        for (int delayMs : Helpers.range(-2_000, 2_000, 50)) {
            options.add(UiOptionItem.from(String.format("%s sec", Helpers.toString(delayMs / 1_000f)),
                    optionItem -> {
                        playerData.setAudioDelayMs(delayMs);
                        onSetCallback.run();
                    },
                    delayMs == playerData.getAudioDelayMs()));
        }

        return OptionCategory.from(AUDIO_DELAY_ID, OptionCategory.TYPE_RADIO, title, options);
    }
}
