package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;

import java.util.ArrayList;
import java.util.List;

public class AboutPresenter {
    private static final String DONATE_URL = "https://www.donationalerts.com/r/firsthash";
    private static final String WEB_SITE_URL = "https://github.com/yuliskov/SmartTubeNext";
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
                mContext.getString(R.string.web_site),
                option -> Helpers.openLink(WEB_SITE_URL, mContext)));

        options.add(UiOptionItem.from(
                mContext.getString(R.string.donation),
                option -> Helpers.openLink(DONATE_URL, mContext)));

        AppSettingsPresenter settingsPresenter = AppSettingsPresenter.instance(mContext);
        settingsPresenter.clear();
        settingsPresenter.appendStringsCategory(mContext.getString(R.string.dialog_about), options);
        settingsPresenter.showDialog();
    }
}
