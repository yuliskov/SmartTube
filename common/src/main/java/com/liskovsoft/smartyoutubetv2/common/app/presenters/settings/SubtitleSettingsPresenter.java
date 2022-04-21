package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;

import java.util.ArrayList;
import java.util.List;

public class SubtitleSettingsPresenter extends BasePresenter<Void> {
    private final PlayerData mPlayerData;

    public SubtitleSettingsPresenter(Context context) {
        super(context);
        mPlayerData = PlayerData.instance(context);
    }

    public static SubtitleSettingsPresenter instance(Context context) {
        return new SubtitleSettingsPresenter(context);
    }

    public void show() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());
        settingsPresenter.clear();

        // Can't work properly. There is no robust language detection.
        //appendSubtitleLanguageCategory(settingsPresenter);
        appendSubtitleStyleCategory(settingsPresenter);
        appendSubtitleSizeCategory(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.subtitle_category_title));
    }

    //private void appendSubtitleLanguageCategory(AppSettingsPresenter settingsPresenter) {
    //    String subtitleLanguageTitle = getContext().getString(R.string.subtitle_language);
    //    String subtitlesDisabled = getContext().getString(R.string.subtitles_disabled);
    //
    //    LangUpdater langUpdater = new LangUpdater(getContext());
    //    Map<String, String> locales = langUpdater.getSupportedLocales();
    //    FormatItem currentFormat = mPlayerData.getFormat(FormatItem.TYPE_SUBTITLE);
    //
    //    List<OptionItem> options = new ArrayList<>();
    //
    //    options.add(UiOptionItem.from(
    //            subtitlesDisabled, option -> mPlayerData.setFormat(FormatItem.fromLanguage(null)),
    //            currentFormat == null || currentFormat.equals(FormatItem.fromLanguage(null))));
    //
    //    for (Entry<String, String> entry : locales.entrySet()) {
    //        if (entry.getValue().isEmpty()) {
    //            // Remove default language entry
    //            continue;
    //        }
    //
    //        options.add(UiOptionItem.from(
    //                entry.getKey(), option -> mPlayerData.setFormat(FormatItem.fromLanguage(entry.getValue())),
    //                FormatItem.fromLanguage(entry.getValue()).equals(currentFormat)));
    //    }
    //
    //    settingsPresenter.appendRadioCategory(subtitleLanguageTitle, options);
    //}

    private void appendSubtitleStyleCategory(AppDialogPresenter settingsPresenter) {
        OptionCategory category = AppDialogUtil.createSubtitleStylesCategory(getContext(), mPlayerData);
        settingsPresenter.appendRadioCategory(category.title, category.options);
    }

    private void appendSubtitleSizeCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int scalePercent : Helpers.range(10, 200, 10)) {
            float scale = scalePercent / 100f;
            options.add(UiOptionItem.from(String.format("%sx", scale),
                    optionItem -> mPlayerData.setSubtitleScale(scale),
                    Helpers.floatEquals(scale, mPlayerData.getSubtitleScale())));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.subtitle_scale), options);
    }
}
