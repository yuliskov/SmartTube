package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;

import java.util.ArrayList;
import java.util.List;

public class MainUISettingsPresenter {
    private final Context mContext;
    private final MainUIData mMainUIData;

    public MainUISettingsPresenter(Context context) {
        mContext = context;
        mMainUIData = MainUIData.instance(context);
    }

    public static MainUISettingsPresenter instance(Context context) {
        return new MainUISettingsPresenter(context.getApplicationContext());
    }

    public void show() {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(
                mContext.getString(R.string.animated_previews),
                option -> mMainUIData.enableAnimatedPreviews(option.isSelected()),
                mMainUIData.isAnimatedPreviewsEnabled()));

        AppSettingsPresenter settingsPresenter = AppSettingsPresenter.instance(mContext);
        settingsPresenter.clear();
        settingsPresenter.appendCheckedCategory(mContext.getString(R.string.dialog_main_ui), options);
        settingsPresenter.showDialog();
    }
}
