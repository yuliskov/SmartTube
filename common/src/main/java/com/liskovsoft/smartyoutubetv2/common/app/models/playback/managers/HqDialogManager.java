package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import android.app.Activity;
import android.os.Build.VERSION;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;

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
    private boolean mBlockEngine;
    private boolean mEnablePIP;

    @Override
    public void onActivity(Activity activity) {
        super.onActivity(activity);

        mSettingsPresenter = AppSettingsPresenter.instance(mActivity);
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

    @Override
    public void onEngineInitialized() {
        updateBackgroundPlayback();
    }

    @Override
    public void onHighQualityClicked() {
        addQualityCategories();
        addBackgroundPlaybackCategory();

        if (VERSION.SDK_INT < 25) {
            // Old Android fix: don't destroy player while dialog is open
            mController.blockEngine(true);
        }

        mSettingsPresenter.clear();

        createRadioOptions();
        createCheckedOptions();
        createSingleOptions();

        mSettingsPresenter.showDialog(mActivity.getString(R.string.playback_settings), this::updateBackgroundPlayback);
    }

    private void updateBackgroundPlayback() {
        if (mBlockEngine) {
            // return to the player regardless the last activity user watched in moment exiting to HOME
            ViewManager.instance(mActivity).blockTop(mActivity);
        } else {
            ViewManager.instance(mActivity).blockTop(null);
        }

        mController.blockEngine(mBlockEngine);
        mController.enablePIP(mEnablePIP);
    }

    private void addBackgroundPlaybackCategory() {
        String categoryTitle = mActivity.getString(R.string.category_background_playback);

        List<OptionItem> options = new ArrayList<>();
        options.add(UiOptionItem.from(mActivity.getString(R.string.option_background_playback_off),
                optionItem -> {
                    mBlockEngine = false;
                    mEnablePIP = false;
                    updateBackgroundPlayback();
                }, !mBlockEngine && !mEnablePIP));
        options.add(UiOptionItem.from(mActivity.getString(R.string.option_background_playback_all),
                optionItem -> {
                    mBlockEngine = true;
                    mEnablePIP = true;
                    updateBackgroundPlayback();
                }, mEnablePIP && mBlockEngine));
        options.add(UiOptionItem.from(mActivity.getString(R.string.option_background_playback_only_audio),
                optionItem -> {
                    mBlockEngine = true;
                    mEnablePIP = false;
                    updateBackgroundPlayback();
                }, mBlockEngine && !mEnablePIP));

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

    private void createSingleOptions() {
        for (OptionItem option : mSingleOptions.values()) {
            mSettingsPresenter.appendSingleSwitch(option);
        }
    }

    private void createCheckedOptions() {
        for (String key : mCheckedCategories.keySet()) {
            mSettingsPresenter.appendCheckedCategory(key, mCheckedCategories.get(key));
        }
    }

    private void createRadioOptions() {
        for (String key : mRadioCategories.keySet()) {
            mSettingsPresenter.appendRadioCategory(key, mRadioCategories.get(key));
        }
    }
}
