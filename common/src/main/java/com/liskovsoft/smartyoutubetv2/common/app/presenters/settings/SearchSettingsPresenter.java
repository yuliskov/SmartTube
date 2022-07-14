package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
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
        settingsPresenter.clear();
        
        appendMiscCategory(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.dialog_search));
    }

    private void appendMiscCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.use_alt_speech_recognizer),
                option -> mSearchData.enableAltSpeechRecognizer(option.isSelected()),
                mSearchData.isAltSpeechRecognizerEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.instant_voice_search),
                option -> mSearchData.enableInstantVoiceSearch(option.isSelected()),
                mSearchData.isInstantVoiceSearchEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.focus_on_search_results),
                option -> mSearchData.enableFocusOnResults(option.isSelected()),
                mSearchData.isFocusOnResultsEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.keyboard_auto_show),
                option -> mSearchData.enableKeyboardAutoShow(option.isSelected()),
                mSearchData.isKeyboardAutoShowEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.search_background_playback),
                option -> mSearchData.enableBackgroundPlayback(option.isSelected()),
                mSearchData.isBackgroundPlaybackEnabled()));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.dialog_search), options);
    }
}
