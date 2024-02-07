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
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

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
        
        appendSwitches(settingsPresenter);
        appendThumbQuality(settingsPresenter);
        appendLinks(settingsPresenter);

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

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.dearrow_not_submitted_thumbs), options);
    }

    private void appendSwitches(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.crowdsoursed_titles),
                optionItem -> mDeArrowData.replaceTitles(optionItem.isSelected()),
                mDeArrowData.isReplaceTitlesEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.crowdsourced_thumbnails),
                optionItem -> mDeArrowData.replaceThumbnails(optionItem.isSelected()),
                mDeArrowData.isReplaceThumbnailsEnabled()));

        for (OptionItem item : options) {
            settingsPresenter.appendSingleSwitch(item);
        }
    }

    private void appendLinks(AppDialogPresenter settingsPresenter) {
        OptionItem webSiteOption = UiOptionItem.from(getContext().getString(R.string.about_dearrow),
                option -> Utils.openLink(getContext(), getContext().getString(R.string.dearrow_provider_url)));

        OptionItem statsCheckOption = UiOptionItem.from(getContext().getString(R.string.dearrow_status),
                option -> Utils.openLink(getContext(), getContext().getString(R.string.dearrow_status_url)));

        settingsPresenter.appendSingleButton(webSiteOption);
        settingsPresenter.appendSingleButton(statsCheckOption);
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
