package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.LangUpdater;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class LanguageSettingsPresenter extends BasePresenter<Void> {
    private final LangUpdater mLangUpdater;
    private boolean mRestartApp;

    public LanguageSettingsPresenter(Context context) {
        super(context);
        mLangUpdater = new LangUpdater(context);
    }

    public static LanguageSettingsPresenter instance(Context context) {
        return new LanguageSettingsPresenter(context);
    }

    public void show() {
        AppSettingsPresenter settingsPresenter = AppSettingsPresenter.instance(getContext());
        settingsPresenter.clear();

        appendLanguageCategory(settingsPresenter);
        appendCountryCategory(settingsPresenter);

        settingsPresenter.showDialog(() -> {
            if (mRestartApp) {
                mRestartApp = false;
                MessageHelpers.showLongMessage(getContext(), R.string.msg_restart_app);
            }
        });
    }

    private void appendLanguageCategory(AppSettingsPresenter settingsPresenter) {
        Map<String, String> locales = mLangUpdater.getSupportedLocales();
        String language = mLangUpdater.getPreferredLocale();

        List<OptionItem> options = new ArrayList<>();

        for (Entry<String, String> entry : locales.entrySet()) {
            options.add(UiOptionItem.from(
                    entry.getKey(),
                    option -> {
                        mLangUpdater.setPreferredLocale(entry.getValue());
                        mRestartApp = true;
                    },
                    entry.getValue().equals(language)));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.dialog_select_language), options);
    }

    private void appendCountryCategory(AppSettingsPresenter settingsPresenter) {

    }
}
