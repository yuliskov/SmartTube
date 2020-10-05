package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import android.app.Activity;
import android.os.Build.VERSION;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackEngineController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;
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
    private final List<Runnable> mHideListeners = new ArrayList<>();

    @Override
    public void onActivity(Activity activity) {
        super.onActivity(activity);

        mSettingsPresenter = AppSettingsPresenter.instance(mActivity);
    }

    @Override
    public void onController(PlaybackController controller) {
        super.onController(controller);

        controller.setBuffer(AppPrefs.instance(mActivity).getVideoBufferType(PlaybackEngineController.BUFFER_LOW));
    }

    private void addQualityCategories() {
        List<FormatItem> videoFormats = mController.getVideoFormats();
        String videoFormatsTitle = mActivity.getString(R.string.video_formats_title);

        List<FormatItem> audioFormats = mController.getAudioFormats();
        String audioFormatsTitle = mActivity.getString(R.string.audio_formats_title);

        addRadioCategory(videoFormatsTitle,
                UiOptionItem.from(videoFormats,
                        option -> mController.selectFormat(UiOptionItem.toFormat(option)),
                        mActivity.getString(R.string.default_video_option)));
        addRadioCategory(audioFormatsTitle,
                UiOptionItem.from(audioFormats,
                        option -> mController.selectFormat(UiOptionItem.toFormat(option)),
                        mActivity.getString(R.string.default_audio_option)));
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

    @Override
    public void onEngineInitialized() {
        updateBackgroundPlayback();
    }

    @Override
    public void onHighQualityClicked() {
        if (VERSION.SDK_INT < 25) {
            // Old Android fix: don't destroy player while dialog is open
            mController.blockEngine(true);
        }

        mSettingsPresenter.clear();

        addQualityCategories();
        addBackgroundPlaybackCategory();
        addVideoBufferCategory();

        appendRadioOptions();
        appendCheckedOptions();
        appendSingleOptions();

        mSettingsPresenter.showDialog(mActivity.getString(R.string.playback_settings), this::onDialogHide);
    }

    private void onDialogHide() {
        updateBackgroundPlayback();

        for (Runnable listener : mHideListeners) {
            listener.run();
        }
    }

    private void updateBackgroundPlayback() {
        if (mEnableBackgroundAudio || mEnablePIP) {
            // return to the player regardless the last activity user watched in moment exiting to HOME
            ViewManager.instance(mActivity).blockTop(mActivity);
        } else {
            ViewManager.instance(mActivity).blockTop(null);
        }

        mController.blockEngine(mEnableBackgroundAudio);
        mController.enablePIP(mEnablePIP);
    }

    private void addBackgroundPlaybackCategory() {
        String categoryTitle = mActivity.getString(R.string.category_background_playback);

        List<OptionItem> options = new ArrayList<>();
        options.add(UiOptionItem.from(mActivity.getString(R.string.option_background_playback_off),
                optionItem -> {
                    mEnableBackgroundAudio = false;
                    mEnablePIP = false;
                    updateBackgroundPlayback();
                }, !mEnableBackgroundAudio && !mEnablePIP));
        options.add(UiOptionItem.from(mActivity.getString(R.string.option_background_playback_all),
                optionItem -> {
                    mEnableBackgroundAudio = false;
                    mEnablePIP = true;
                    updateBackgroundPlayback();
                }, mEnablePIP && !mEnableBackgroundAudio));
        options.add(UiOptionItem.from(mActivity.getString(R.string.option_background_playback_only_audio),
                optionItem -> {
                    mEnableBackgroundAudio = true;
                    mEnablePIP = false;
                    updateBackgroundPlayback();
                }, mEnableBackgroundAudio && !mEnablePIP));

        addRadioCategory(categoryTitle, options);
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
