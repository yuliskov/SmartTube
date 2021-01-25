package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.ContentBlockData;

public class BlockSettingsPresenter extends BasePresenter<Void> {
    private final ContentBlockData mContentBlockData;

    public BlockSettingsPresenter(Context context) {
        super(context);
        mContentBlockData = ContentBlockData.instance(context);
    }

    public static BlockSettingsPresenter instance(Context context) {
        return new BlockSettingsPresenter(context);
    }

    public void show() {
        AppSettingsPresenter settingsPresenter = AppSettingsPresenter.instance(getContext());
        settingsPresenter.clear();
        
        appendSponsorBlockSwitch(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.settings_block));
    }

    private void appendSponsorBlockSwitch(AppSettingsPresenter settingsPresenter) {
        OptionItem sponsorBlockOption = UiOptionItem.from(
                "SponsorBlock",
                option -> mContentBlockData.setSponsorBlockEnabled(option.isSelected()),
                mContentBlockData.isSponsorBlockEnabled()
        );

        settingsPresenter.appendSingleSwitch(sponsorBlockOption);
    }
}
