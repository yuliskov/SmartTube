package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.DeArrowData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.ClickbaitRemover;

import java.util.ArrayList;
import java.util.List;

public class DeArrowSettingsPresenter extends BasePresenter<Void> {
    private final MainUIData mMainUIData;
    private final DeArrowData mDeArrowData;

    private DeArrowSettingsPresenter(Context context) {
        super(context);
        mMainUIData = MainUIData.instance(context);
        mDeArrowData = DeArrowData.instance(context);
    }

    public static DeArrowSettingsPresenter instance(Context context) {
        return new DeArrowSettingsPresenter(context);
    }

    public void show(Runnable onFinish) {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        //appendDeArrowSwitch(settingsPresenter);
        appendThumbQuality(settingsPresenter);
        appendMiscSection(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.dearrow_provider), onFinish);
    }

    public void show() {
        show(null);
    }

    private void appendThumbQuality(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.thumb_quality_default, ClickbaitRemover.THUMB_QUALITY_DEFAULT},
                {R.string.thumb_quality_start, ClickbaitRemover.THUMB_QUALITY_START},
                {R.string.thumb_quality_middle, ClickbaitRemover.THUMB_QUALITY_MIDDLE},
                {R.string.thumb_quality_end, ClickbaitRemover.THUMB_QUALITY_END}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]),
                    optionItem -> mMainUIData.setThumbQuality(pair[1]),
                    mMainUIData.getThumbQuality() == pair[1]
            ));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.card_content), options);
    }

    private void appendMiscSection(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.replace_titles),
                optionItem -> mDeArrowData.replaceTitles(optionItem.isSelected()),
                mDeArrowData.isReplaceTitlesEnabled()));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.player_other), options);
    }

    private void appendDeArrowSwitch(AppDialogPresenter settingsPresenter) {
        String title = String.format(
                "%s (%s)",
                getContext().getString(R.string.dearrow_provider),
                getContext().getString(R.string.dearrow_provider_url)
        );
        OptionItem sponsorBlockOption = UiOptionItem.from(title,
                option -> mDeArrowData.enableDeArrow(option.isSelected()),
                mDeArrowData.isDeArrowEnabled()
        );

        settingsPresenter.appendSingleSwitch(sponsorBlockOption);
    }
}
