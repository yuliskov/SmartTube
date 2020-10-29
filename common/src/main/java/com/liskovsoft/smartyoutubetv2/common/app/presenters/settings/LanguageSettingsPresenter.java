package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.language.LangUpdater;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

public class LanguageSettingsPresenter {
    private final Context mContext;
    private final LangUpdater mLangUpdater;

    public LanguageSettingsPresenter(Context context) {
        mContext = context;
        mLangUpdater = new LangUpdater(context);
    }

    public static LanguageSettingsPresenter instance(Context context) {
        return new LanguageSettingsPresenter(context.getApplicationContext());
    }

    public void show() {
        HashMap<String, String> locales = mLangUpdater.getSupportedLocales();
        String language = mLangUpdater.getPreferredLocale();

        List<OptionItem> options = new ArrayList<>();
        
        for (Entry<String, String> entry : locales.entrySet()) {
            options.add(UiOptionItem.from(
                    entry.getKey(), option -> mLangUpdater.setPreferredLocale(entry.getValue()), entry.getValue().equals(language)));
        }

        AppSettingsPresenter settingsPresenter = AppSettingsPresenter.instance(mContext);
        settingsPresenter.clear();
        settingsPresenter.appendRadioCategory(mContext.getString(R.string.dialog_select_language), options);
        settingsPresenter.showDialog(() -> ViewManager.instance(mContext).restartApp(mContext));
    }
}
