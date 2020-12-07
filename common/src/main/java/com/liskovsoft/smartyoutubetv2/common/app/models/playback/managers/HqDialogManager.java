package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import android.os.Build;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.BuildConfig;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackEngineController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem.Preset;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HqDialogManager extends PlayerEventListenerHelper {
    private static final String TAG = HqDialogManager.class.getSimpleName();
    private AppSettingsPresenter mSettingsPresenter;
    // NOTE: using map, because same item could be changed time to time
    private final Map<String, List<OptionItem>> mCheckedCategories = new LinkedHashMap<>();
    private final Map<String, List<OptionItem>> mRadioCategories = new LinkedHashMap<>();
    private final Map<CharSequence, OptionItem> mSingleOptions = new LinkedHashMap<>();
    private boolean mEnableBackgroundAudio;
    private boolean mEnablePIP;
    private boolean mEnablePlayBehind;
    private final Set<Runnable> mHideListeners = new HashSet<>();
    private final StateUpdater mStateUpdater;

    public HqDialogManager(StateUpdater stateUpdater) {
        mStateUpdater = stateUpdater;
    }

    @Override
    public void onInitDone() {
        mSettingsPresenter = AppSettingsPresenter.instance(getActivity());
        getController().setBuffer(AppPrefs.instance(getActivity()).getVideoBufferType(PlaybackEngineController.BUFFER_LOW));
    }

    @Override
    public void onEngineInitialized() {
        updateBackgroundPlayback();
    }

    @Override
    public void onHighQualityClicked() {
        mSettingsPresenter.clear();

        addQualityCategories();
        addVideoBufferCategory();
        if (!BuildConfig.FLAVOR.equals("stbolshoetv")) {
            addPresetsCategory();
            addBackgroundPlaybackCategory();
        }

        internalStuff();

        mSettingsPresenter.showDialog(getActivity().getString(R.string.playback_settings), this::onDialogHide);
    }

    private void addQualityCategories() {
        List<FormatItem> videoFormats = getController().getVideoFormats();
        String videoFormatsTitle = getActivity().getString(R.string.title_video_formats);

        List<FormatItem> audioFormats = getController().getAudioFormats();
        String audioFormatsTitle = getActivity().getString(R.string.title_audio_formats);

        addRadioCategory(videoFormatsTitle,
                UiOptionItem.from(videoFormats, this::selectFormatOption));
        addRadioCategory(audioFormatsTitle,
                UiOptionItem.from(audioFormats, this::selectFormatOption));
    }

    private void selectFormatOption(OptionItem option) {
        getController().selectFormat(UiOptionItem.toFormat(option));
        if (getController().hasNoMedia()) {
            getController().reloadPlayback();
        }
    }

    private void addVideoBufferCategory() {
        String videoBuffer = getActivity().getString(R.string.video_buffer);
        List<OptionItem> optionItems = new ArrayList<>();
        optionItems.add(createBufferOption(R.string.video_buffer_size_low, PlaybackEngineController.BUFFER_LOW));
        optionItems.add(createBufferOption(R.string.video_buffer_size_med, PlaybackEngineController.BUFFER_MED));
        optionItems.add(createBufferOption(R.string.video_buffer_size_high, PlaybackEngineController.BUFFER_HIGH));
        addRadioCategory(videoBuffer, optionItems);
    }

    private OptionItem createBufferOption(int titleResId, int val) {
        return UiOptionItem.from(
                getActivity().getString(titleResId),
                optionItem -> {
                    getController().setBuffer(val);
                    AppPrefs.instance(getActivity()).setVideoBufferType(val);
                    getController().restartEngine();
                },
                getController().getBuffer() == val);
    }

    private void internalStuff() {
        appendRadioOptions();
        appendCheckedOptions();
        appendSingleOptions();
    }

    private void onDialogHide() {
        updateBackgroundPlayback();

        for (Runnable listener : mHideListeners) {
            listener.run();
        }
    }

    private void updateBackgroundPlayback() {
        if (mEnableBackgroundAudio || mEnablePIP || mEnablePlayBehind) {
            // return to the player regardless the last activity user watched in moment exiting to HOME
            ViewManager.instance(getActivity()).blockTop(getActivity());
        } else {
            ViewManager.instance(getActivity()).blockTop(null);
        }

        getController().blockEngine(mEnableBackgroundAudio);
        getController().enablePIP(mEnablePIP);
        getController().enablePlayBehind(mEnablePlayBehind);
    }

    private void addBackgroundPlaybackCategory() {
        String categoryTitle = getActivity().getString(R.string.category_background_playback);

        List<OptionItem> options = new ArrayList<>();
        options.add(UiOptionItem.from(getActivity().getString(R.string.option_background_playback_off),
                optionItem -> {
                    mEnableBackgroundAudio = false;
                    mEnablePIP = false;
                    mEnablePlayBehind = false;
                    updateBackgroundPlayback();
                }, !mEnableBackgroundAudio && !mEnablePIP && !mEnablePlayBehind));
        if (Helpers.isAndroidTV(getActivity()) && Build.VERSION.SDK_INT < 26) { // useful only for pre-Oreo UI
            options.add(UiOptionItem.from(getActivity().getString(R.string.option_background_playback_behind) + " (Android TV 5,6,7)",
                    optionItem -> {
                        mEnableBackgroundAudio = false;
                        mEnablePIP = false;
                        mEnablePlayBehind = true;
                        updateBackgroundPlayback();
                    }, mEnablePlayBehind && !mEnablePIP && !mEnableBackgroundAudio));
        }
        options.add(UiOptionItem.from(getActivity().getString(R.string.option_background_playback_pip),
                optionItem -> {
                    mEnableBackgroundAudio = false;
                    mEnablePIP = true;
                    mEnablePlayBehind = false;
                    updateBackgroundPlayback();
                }, mEnablePIP && !mEnableBackgroundAudio && !mEnablePlayBehind));
        options.add(UiOptionItem.from(getActivity().getString(R.string.option_background_playback_only_audio),
                optionItem -> {
                    mEnableBackgroundAudio = true;
                    mEnablePIP = false;
                    mEnablePlayBehind = false;
                    updateBackgroundPlayback();
                }, mEnableBackgroundAudio && !mEnablePIP && !mEnablePlayBehind));

        addRadioCategory(categoryTitle, options);
    }

    private void addPresetsCategory() {
        Preset[] presets = {
                new Preset("SD     30fps    avc", "640,360,30,avc"),
                new Preset("SD     30fps    vp9", "640,360,30,vp9"),
                new Preset("SD     60fps    avc", "640,360,60,avc"),
                new Preset("SD     60fps    vp9", "640,360,60,vp9"),
                new Preset("HD     30fps    avc", "1280,720,30,avc"),
                new Preset("HD     30fps    vp9", "1280,720,30,vp9"),
                new Preset("HD     60fps    avc", "1280,720,60,avc"),
                new Preset("HD     60fps    vp9", "1280,720,60,vp9"),
                new Preset("FHD    30fps    avc", "1920,1080,30,avc"),
                new Preset("FHD    30fps    vp9", "1920,1080,30,vp9"),
                new Preset("FHD    30fps    vp9+hdr", "1920,1080,30,vp9.2"),
                new Preset("FHD    60fps    avc", "1920,1080,60,avc"),
                new Preset("FHD    60fps    vp9", "1920,1080,60,vp9"),
                new Preset("FHD    60fps    vp9+hdr", "1920,1080,60,vp9.2"),
                new Preset("2K     30fps    vp9", "2560,1440,30,vp9"),
                new Preset("2K     30fps    vp9+hdr", "2560,1440,30,vp9.2"),
                new Preset("2K     60fps    vp9", "2560,1440,60,vp9"),
                new Preset("2K     60fps    vp9+hdr", "2560,1440,60,vp9.2"),
                new Preset("4K     30fps    vp9", "3840,2160,30,vp9"),
                new Preset("4K     30fps    vp9+hdr", "3840,2160,30,vp9.2"),
                new Preset("4K     60fps    vp9", "3840,2160,60,vp9"),
                new Preset("4K     60fps    vp9+hdr", "3840,2160,60,vp9.2"),
                new Preset("8K     30fps    vp9", "7680,4320,30,vp9"),
                new Preset("8K     30fps    vp9+hdr", "7680,4320,30,vp9.2"),
                new Preset("8K     60fps    vp9", "7680,4320,60,vp9"),
                new Preset("8K     60fps    vp9+hdr", "7680,4320,60,vp9.2")
        };

        addRadioCategory(getActivity().getString(R.string.title_video_presets), fromPresets(presets));
    }

    private List<OptionItem> fromPresets(Preset[] presets) {
        List<OptionItem> result = new ArrayList<>();

        if (mStateUpdater.getVideoPreset() != null) {
            for (Preset preset : presets) {
                result.add(0, UiOptionItem.from(preset.name,
                        option -> getController().selectFormat(preset.format),
                        mStateUpdater.getVideoPreset().equals(preset.format)));
            }
        }

        return result;
    }

    public void addSingleOption(OptionItem option) {
        mSingleOptions.put(option.getTitle(), option);
    }

    public void addCheckedCategory(String categoryTitle, List<OptionItem> options) {
        mCheckedCategories.put(categoryTitle, options);
    }

    public void removeCategory(String categoryTitle) {
        mCheckedCategories.remove(categoryTitle);
    }

    public void addRadioCategory(String categoryTitle, List<OptionItem> options) {
        mRadioCategories.put(categoryTitle, options);
    }

    public void addOnDialogHide(Runnable listener) {
        mHideListeners.add(listener);
    }

    public void removeOnDialogHide(Runnable listener) {
        mHideListeners.remove(listener);
    }

    private void appendSingleOptions() {
        for (OptionItem option : mSingleOptions.values()) {
            mSettingsPresenter.appendSingleSwitch(option);
        }
    }

    private void appendCheckedOptions() {
        for (String key : mCheckedCategories.keySet()) {
            mSettingsPresenter.appendCheckedCategory(key, mCheckedCategories.get(key));
        }
    }

    private void appendRadioOptions() {
        for (String key : mRadioCategories.keySet()) {
            mSettingsPresenter.appendRadioCategory(key, mRadioCategories.get(key));
        }
    }
}
