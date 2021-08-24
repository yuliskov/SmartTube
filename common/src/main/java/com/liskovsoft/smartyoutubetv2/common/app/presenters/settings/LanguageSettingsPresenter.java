package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.sharedutils.locale.LocaleUpdater;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class LanguageSettingsPresenter extends BasePresenter<Void> {
    private final LocaleUpdater mLangUpdater;
    private boolean mRestartApp;

    public LanguageSettingsPresenter(Context context) {
        super(context);
        mLangUpdater = new LocaleUpdater(context);
    }

    public static LanguageSettingsPresenter instance(Context context) {
        return new LanguageSettingsPresenter(context);
    }

    public void show() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());
        settingsPresenter.clear();

        appendLanguageCategory(settingsPresenter);
        appendCountryCategory(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.settings_language_country), () -> {
            if (mRestartApp) {
                mRestartApp = false;
                MessageHelpers.showLongMessage(getContext(), R.string.msg_restart_app);
            }
        });
    }

    private void appendLanguageCategory(AppDialogPresenter settingsPresenter) {
        Map<String, String> locales = getSupportedLocales();
        String language = mLangUpdater.getPreferredLanguage();

        List<OptionItem> options = new ArrayList<>();

        for (Entry<String, String> entry : locales.entrySet()) {
            options.add(UiOptionItem.from(
                    entry.getKey(),
                    option -> {
                        mLangUpdater.setPreferredLanguage(entry.getValue());
                        mRestartApp = true;
                    },
                    entry.getValue().equals(language)));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.dialog_select_language), options);
    }

    private void appendCountryCategory(AppDialogPresenter settingsPresenter) {
        Map<String, String> countries = getSupportedCountries();
        String country = mLangUpdater.getPreferredCountry();

        List<OptionItem> options = new ArrayList<>();

        for (Entry<String, String> entry : countries.entrySet()) {
            options.add(UiOptionItem.from(
                    entry.getKey(),
                    option -> {
                        mLangUpdater.setPreferredCountry(entry.getValue());
                        mRestartApp = true;
                    },
                    entry.getValue().equals(country)));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.dialog_select_country), options);
    }

    /**
     * Gets map of Human readable locale names and their respective lang codes
     * @return locale name/code map
     */
    private Map<String, String> getSupportedLocales() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put(getContext().getResources().getString(R.string.default_lang), "");
        return Helpers.getMap(getContext().getResources().getStringArray(R.array.supported_languages), "|", map);
    }

    private Map<String, String> getSupportedCountries() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put(getContext().getResources().getString(R.string.default_lang), "");
        return Helpers.getMap(getContext().getResources().getStringArray(R.array.supported_countries), "|", map);
    }
}
