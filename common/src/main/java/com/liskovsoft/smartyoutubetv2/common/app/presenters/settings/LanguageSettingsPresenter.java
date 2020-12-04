package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.misc.LangUpdater;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

public class LanguageSettingsPresenter extends BasePresenter<Void> {
    private final LangUpdater mLangUpdater;

    public LanguageSettingsPresenter(Context context) {
        super(context);
        mLangUpdater = new LangUpdater(context);
    }

    public static LanguageSettingsPresenter instance(Context context) {
        return new LanguageSettingsPresenter(context);
    }

    public void show() {
        HashMap<String, String> locales = mLangUpdater.getSupportedLocales();
        String language = mLangUpdater.getPreferredLocale();

        List<OptionItem> options = new ArrayList<>();
        
        for (Entry<String, String> entry : locales.entrySet()) {
            options.add(UiOptionItem.from(
                    entry.getKey(), option -> mLangUpdater.setPreferredLocale(entry.getValue()), entry.getValue().equals(language)));
        }

        AppSettingsPresenter settingsPresenter = AppSettingsPresenter.instance(getContext());
        settingsPresenter.clear();
        settingsPresenter.appendRadioCategory(getContext().getString(R.string.dialog_select_language), options);
        settingsPresenter.showDialog(() -> {
            if (!language.equals(mLangUpdater.getPreferredLocale())) {
                ViewManager.instance(getContext()).restartApp();
            }
        });
    }
}
