package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.UIPrefs;

import java.util.ArrayList;
import java.util.List;

public class UISettingsPresenter {
    private final Context mContext;
    private final UIPrefs mUIPrefs;

    public UISettingsPresenter(Context context) {
        mContext = context;
        mUIPrefs = UIPrefs.instance(context);
    }

    public static UISettingsPresenter instance(Context context) {
        return new UISettingsPresenter(context.getApplicationContext());
    }

    public void show() {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(
                mContext.getString(R.string.animated_previews),
                option -> mUIPrefs.enableAnimatedPreviews(option.isSelected()),
                mUIPrefs.isAnimatedPreviewsEnabled()));

        AppSettingsPresenter settingsPresenter = AppSettingsPresenter.instance(mContext);
        settingsPresenter.clear();
        settingsPresenter.appendCheckedCategory(mContext.getString(R.string.dialog_ui), options);
        settingsPresenter.showDialog();
    }
}
