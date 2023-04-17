package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HQDialogManager extends PlayerEventListenerHelper {
    private static final String TAG = HQDialogManager.class.getSimpleName();
    private static final int VIDEO_FORMATS_ID = 132;
    private static final int AUDIO_FORMATS_ID = 133;
    // NOTE: using map, because same item could be changed time to time
    private final Map<Integer, OptionCategory> mCategories = new LinkedHashMap<>();
    private final Map<Integer, OptionCategory> mCategoriesInt = new LinkedHashMap<>();
    private final Set<Runnable> mHideListeners = new HashSet<>();
    private final VideoStateManager mStateUpdater;
    private PlayerData mPlayerData;
    private AppDialogPresenter mAppDialogPresenter;;

    public HQDialogManager(VideoStateManager stateUpdater) {
        mStateUpdater = stateUpdater;
    }

    @Override
    public void onInitDone() {
        mPlayerData = PlayerData.instance(getActivity());
        mAppDialogPresenter = AppDialogPresenter.instance(getActivity());
    }

    @Override
    public void onViewResumed() {
        updateBackgroundPlayback();
    }

    @Override
    public void onHighQualityClicked() {
        addQualityCategories();
        addVideoBufferCategory();
        //addPresetsCategory();
        //addAudioDelayCategory();
        //addBackgroundPlaybackCategory();

        appendOptions(mCategoriesInt);
        appendOptions(mCategories);

        mAppDialogPresenter.showDialog(getActivity().getString(R.string.playback_settings), this::onDialogHide);
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

        // Make result easily be spotted by the user
        if (formatItem.getType() == FormatItem.TYPE_VIDEO) {
            getController().showOverlay(false);
        }
    }

    private void addVideoBufferCategory() {
        addCategoryInt(AppDialogUtil.createVideoBufferCategory(getActivity(), mPlayerData,
                () -> getController().restartEngine()));
    }

    private void addAudioDelayCategory() {
        addCategoryInt(AppDialogUtil.createAudioShiftCategory(getActivity(), mPlayerData,
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

        if (getController() != null) {
            getController().setBackgroundMode(mPlayerData.getBackgroundMode());
        }
    }

    private void addBackgroundPlaybackCategory() {
        OptionCategory category =
                AppDialogUtil.createBackgroundPlaybackCategory(getActivity(), mPlayerData, GeneralData.instance(getActivity()), this::updateBackgroundPlayback);

        addCategoryInt(category);
    }

    private void addPresetsCategory() {
        addCategoryInt(AppDialogUtil.createVideoPresetsCategory(
                getActivity(), () -> {
                    if (getController() == null) {
                        return;
                    }

                    FormatItem format = mPlayerData.getFormat(FormatItem.TYPE_VIDEO);
                    getController().setFormat(format);

                    if (!getController().containsMedia()) {
                        getController().reloadPlayback();
                    }

                    // Make result easily be spotted by the user
                    getController().showOverlay(false);
                }
        ));
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
                    mAppDialogPresenter.appendRadioCategory(category.title, category.options);
                    break;
                case OptionCategory.TYPE_CHECKED:
                    mAppDialogPresenter.appendCheckedCategory(category.title, category.options);
                    break;
                case OptionCategory.TYPE_STRING:
                    mAppDialogPresenter.appendStringsCategory(category.title, category.options);
                    break;
                case OptionCategory.TYPE_LONG_TEXT:
                    mAppDialogPresenter.appendLongTextCategory(category.title, category.option);
                    break;
                case OptionCategory.TYPE_SINGLE:
                    mAppDialogPresenter.appendSingleSwitch(category.option);
                    break;
            }
        }
    }
}
