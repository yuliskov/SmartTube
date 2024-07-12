package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;

import java.util.ArrayList;
import java.util.List;

public class SearchSettingsPresenter extends BasePresenter<Void> {
    private final SearchData mSearchData;

    public SearchSettingsPresenter(Context context) {
        super(context);
        mSearchData = SearchData.instance(context);
    }

    public static SearchSettingsPresenter instance(Context context) {
        return new SearchSettingsPresenter(context);
    }

    public void show() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appendSpeechRecognizerCategory(settingsPresenter);
        appendMiscCategory(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.dialog_search), () -> {
            if (mSearchData.isSearchHistoryDisabled()) {
                MediaServiceManager.instance().clearSearchHistory();
            }
        });
    }

    private void appendSpeechRecognizerCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.speech_recognizer_system, SearchData.SPEECH_RECOGNIZER_SYSTEM},
                {R.string.speech_recognizer_external_1, SearchData.SPEECH_RECOGNIZER_INTENT},
                {R.string.speech_recognizer_external_2, SearchData.SPEECH_RECOGNIZER_GOTEV}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]),
                    optionItem -> mSearchData.setSpeechRecognizerType(pair[1]),
                    mSearchData.getSpeechRecognizerType() == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.speech_engine), options);
    }

    private void appendMiscCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        //options.add(UiOptionItem.from(getContext().getString(R.string.disable_popular_searches),
        //        option -> mSearchData.disablePopularSearches(option.isSelected()),
        //        mSearchData.isPopularSearchesDisabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.disable_search_history),
                option -> mSearchData.disableSearchHistory(option.isSelected()),
                mSearchData.isSearchHistoryDisabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.search_background_playback),
                option -> mSearchData.enableTempBackgroundMode(option.isSelected()),
                mSearchData.isTempBackgroundModeEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.instant_voice_search),
                option -> mSearchData.enableInstantVoiceSearch(option.isSelected()),
                mSearchData.isInstantVoiceSearchEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.focus_on_search_results),
                option -> mSearchData.enableFocusOnResults(option.isSelected()),
                mSearchData.isFocusOnResultsEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.keyboard_auto_show),
                option -> mSearchData.enableKeyboardAutoShow(option.isSelected()),
                mSearchData.isKeyboardAutoShowEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.keyboard_fix),
                option -> mSearchData.enableKeyboardFix(option.isSelected()),
                mSearchData.isKeyboardFixEnabled()));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.player_other), options);
    }
}
