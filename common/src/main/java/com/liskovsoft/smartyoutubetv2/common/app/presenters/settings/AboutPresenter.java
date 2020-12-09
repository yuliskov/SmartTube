package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.appupdatechecker2.AppUpdateChecker;
import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.BuildConfig;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppUpdatePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;

import java.util.ArrayList;
import java.util.List;

public class AboutPresenter extends BasePresenter<Void> {
    private static final String WEB_SITE_URL = "https://github.com/yuliskov/SmartTubeNext";
    private static final String DONATIONALERTS_URL = "https://www.donationalerts.com/r/firsthash";
    private static final String QIWI_URL = "https://qiwi.com/n/GUESS025";
    private static final String PRIVATBANK_URL = "https://privatbank.ua/ru/sendmoney?payment=9e46a6ef78";

    public AboutPresenter(Context context) {
        super(context);
    }

    public static AboutPresenter instance(Context context) {
        return new AboutPresenter(context);
    }

    public void show() {
        String mainTitle = String.format("%s %s",
                getContext().getString(R.string.app_name),
                AppInfoHelpers.getAppVersionName(getContext()));

        AppSettingsPresenter settingsPresenter = AppSettingsPresenter.instance(getContext());
        settingsPresenter.clear();

        if (!BuildConfig.FLAVOR.equals("stbolshoetv")) {
            appendAutoUpdateSwitch(settingsPresenter);

            appendUpdateCheckButton(settingsPresenter);
        }
        appendSiteLink(settingsPresenter);

        appendDonation(settingsPresenter);

        settingsPresenter.showDialog(mainTitle);
    }

    private void appendAutoUpdateSwitch(AppSettingsPresenter settingsPresenter) {
        AppUpdateChecker mUpdateChecker = new AppUpdateChecker(getContext(), null);

        settingsPresenter.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.check_updates_auto), optionItem -> {
            mUpdateChecker.enableUpdateCheck(optionItem.isSelected());
        }, mUpdateChecker.isUpdateCheckEnabled()));
    }

    private void appendUpdateCheckButton(AppSettingsPresenter settingsPresenter) {
        OptionItem updateCheckOption = UiOptionItem.from(
                getContext().getString(R.string.check_for_updates),
                option -> AppUpdatePresenter.instance(getContext()).start(true));

        settingsPresenter.appendSingleButton(updateCheckOption);
    }

    private void appendSiteLink(AppSettingsPresenter settingsPresenter) {
        OptionItem webSiteOption = UiOptionItem.from(String.format("%s (GitHub)", getContext().getString(R.string.web_site)),
                option -> Helpers.openLink(WEB_SITE_URL, getContext()));

        settingsPresenter.appendSingleButton(webSiteOption);
    }

    private void appendDonation(AppSettingsPresenter settingsPresenter) {
        List<OptionItem> donateOptions = new ArrayList<>();

        donateOptions.add(UiOptionItem.from(
                "PrivatBank (UA)",
                option -> Helpers.openLink(PRIVATBANK_URL, getContext())));

        donateOptions.add(UiOptionItem.from(
                "QIWI (RU)",
                option -> Helpers.openLink(QIWI_URL, getContext())));

        donateOptions.add(UiOptionItem.from(
                "PayPal",
                option -> Helpers.openLink(DONATIONALERTS_URL, getContext())));

        donateOptions.add(UiOptionItem.from(
                "BTC: 1JAT5VVWarVBkpVbNDn8UA8HXNdrukuBSx",
                null));

        settingsPresenter.appendStringsCategory(getContext().getString(R.string.donation), donateOptions);
    }
}
