package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import android.os.Build;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HqDialogManager extends PlayerEventListenerHelper {
    private static final String TAG = HqDialogManager.class.getSimpleName();
    private AppSettingsPresenter mSettingsPresenter;
    // NOTE: using map, because same item could be changed time to time
    private final Map<String, List<OptionItem>> mCheckedCategories = new LinkedHashMap<>();
    private final Map<String, List<OptionItem>> mRadioCategories = new LinkedHashMap<>();
    private final Map<CharSequence, OptionItem> mSingleOptions = new LinkedHashMap<>();
    private boolean mEnableBackgroundAudio;
    private boolean mEnablePIP;
    private boolean mEnableHOME;
    private final List<Runnable> mHideListeners = new ArrayList<>();
    private final StateUpdater mStateUpdater;

    public HqDialogManager(StateUpdater stateUpdater) {
        mStateUpdater = stateUpdater;
    }

    @Override
    public void onInitDone() {
        mSettingsPresenter = AppSettingsPresenter.instance(mActivity);
        mController.setBuffer(AppPrefs.instance(mActivity).getVideoBufferType(PlaybackEngineController.BUFFER_LOW));
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
        addPresetsCategory();
        addBackgroundPlaybackCategory();

        internalStuff();

        mSettingsPresenter.showDialog(mActivity.getString(R.string.playback_settings), this::onDialogHide);
    }

    private void addQualityCategories() {
        List<FormatItem> videoFormats = mController.getVideoFormats();
        String videoFormatsTitle = mActivity.getString(R.string.title_video_formats);

        List<FormatItem> audioFormats = mController.getAudioFormats();
        String audioFormatsTitle = mActivity.getString(R.string.title_audio_formats);

        addRadioCategory(videoFormatsTitle,
                UiOptionItem.from(videoFormats,
                        option -> mController.selectFormat(UiOptionItem.toFormat(option)),
                        mActivity.getString(R.string.video_max_quality)));
        addRadioCategory(audioFormatsTitle,
                UiOptionItem.from(audioFormats,
                        option -> mController.selectFormat(UiOptionItem.toFormat(option)),
                        mActivity.getString(R.string.audio_max_quality)));
    }

    private void addVideoBufferCategory() {
        String videoBuffer = mActivity.getString(R.string.video_buffer);
        List<OptionItem> optionItems = new ArrayList<>();
        optionItems.add(createBufferOption(R.string.video_buffer_size_low, PlaybackEngineController.BUFFER_LOW));
        optionItems.add(createBufferOption(R.string.video_buffer_size_med, PlaybackEngineController.BUFFER_MED));
        optionItems.add(createBufferOption(R.string.video_buffer_size_high, PlaybackEngineController.BUFFER_HIGH));
        addRadioCategory(videoBuffer, optionItems);
    }

    private OptionItem createBufferOption(int titleResId, int val) {
        return UiOptionItem.from(
                mActivity.getString(titleResId),
                optionItem -> {
                    mController.setBuffer(val);
                    AppPrefs.instance(mActivity).setVideoBufferType(val);
                    mController.restartEngine();
                },
                mController.getBuffer() == val);
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
        if (mEnableBackgroundAudio || mEnablePIP || mEnableHOME) {
            // return to the player regardless the last activity user watched in moment exiting to HOME
            ViewManager.instance(mActivity).blockTop(mActivity);
        } else {
            ViewManager.instance(mActivity).blockTop(null);
        }

        mController.blockEngine(mEnableBackgroundAudio);
        mController.enablePIP(mEnablePIP);
        mController.enableHOME(mEnableHOME);
    }

    private void addBackgroundPlaybackCategory() {
        String categoryTitle = mActivity.getString(R.string.category_background_playback);

        List<OptionItem> options = new ArrayList<>();
        options.add(UiOptionItem.from(mActivity.getString(R.string.option_background_playback_off),
                optionItem -> {
                    mEnableBackgroundAudio = false;
                    mEnablePIP = false;
                    mEnableHOME = false;
                    updateBackgroundPlayback();
                }, !mEnableBackgroundAudio && !mEnablePIP && !mEnableHOME));
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) { // useful only for pre-Oreo UI
            options.add(UiOptionItem.from(mActivity.getString(R.string.option_background_playback_home),
                    optionItem -> {
                        mEnableBackgroundAudio = false;
                        mEnablePIP = false;
                        mEnableHOME = true;
                        updateBackgroundPlayback();
                    }, mEnableHOME && !mEnablePIP && !mEnableBackgroundAudio));
        }
        options.add(UiOptionItem.from(mActivity.getString(R.string.option_background_playback_all),
                optionItem -> {
                    mEnableBackgroundAudio = false;
                    mEnablePIP = true;
                    mEnableHOME = false;
                    updateBackgroundPlayback();
                }, mEnablePIP && !mEnableHOME && !mEnableBackgroundAudio));
        options.add(UiOptionItem.from(mActivity.getString(R.string.option_background_playback_only_audio),
                optionItem -> {
                    mEnableBackgroundAudio = true;
                    mEnablePIP = false;
                    mEnableHOME = false;
                    updateBackgroundPlayback();
                }, mEnableBackgroundAudio && !mEnablePIP && !mEnableHOME));

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

        addRadioCategory(mActivity.getString(R.string.title_video_presets), fromPresets(presets));
    }

    private List<OptionItem> fromPresets(Preset[] presets) {
        List<OptionItem> result = new ArrayList<>();

        if (mStateUpdater.getVideoPreset() != null) {
            for (Preset preset : presets) {
                result.add(0, UiOptionItem.from(preset.name,
                        option -> mController.selectFormat(preset.format),
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

    public void addRadioCategory(String categoryTitle, List<OptionItem> options) {
        mRadioCategories.put(categoryTitle, options);
    }

    public void setOnDialogHide(Runnable listener) {
        mHideListeners.add(listener);
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
