package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.update.AppUpdateManager;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;

import java.util.ArrayList;
import java.util.List;

public class AboutPresenter {
    private static final String WEB_SITE_URL = "https://github.com/yuliskov/SmartTubeNext";
    private static final String DONATIONALERTS_URL = "https://www.donationalerts.com/r/firsthash";
    private static final String QIWI_URL = "https://qiwi.com/n/GUESS025";
    private static final String PRIVATBANK_URL = "https://privatbank.ua/ru/sendmoney?payment=9e46a6ef78";
    private final Context mContext;

    public AboutPresenter(Context context) {
        mContext = context;
    }

    public static AboutPresenter instance(Context context) {
        return new AboutPresenter(context.getApplicationContext());
    }

    public void show() {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(
                mContext.getString(R.string.check_for_updates),
                option -> AppUpdateManager.instance(mContext).start(true)));

        options.add(UiOptionItem.from(
                String.format("%s (GitHub)", mContext.getString(R.string.web_site)),
                option -> Helpers.openLink(WEB_SITE_URL, mContext)));

        options.add(UiOptionItem.from(
                String.format("%s (PrivatBank (UA))", mContext.getString(R.string.donation)),
                option -> Helpers.openLink(PRIVATBANK_URL, mContext)));

        options.add(UiOptionItem.from(
                String.format("%s (QIWI (RU))", mContext.getString(R.string.donation)),
                option -> Helpers.openLink(QIWI_URL, mContext)));

        options.add(UiOptionItem.from(
                String.format("%s (PayPal)", mContext.getString(R.string.donation)),
                option -> Helpers.openLink(DONATIONALERTS_URL, mContext)));

        options.add(UiOptionItem.from(
                String.format("%s (BTC: 1JAT5VVWarVBkpVbNDn8UA8HXNdrukuBSx)", mContext.getString(R.string.donation)),
                null));

        AppSettingsPresenter settingsPresenter = AppSettingsPresenter.instance(mContext);
        settingsPresenter.clear();
        String mainTitle = String.format("%s %s",
                mContext.getString(R.string.app_name),
                AppInfoHelpers.getAppVersionName(mContext));
        settingsPresenter.appendStringsCategory(mainTitle, options);
        settingsPresenter.showDialog(mainTitle);
    }
}
