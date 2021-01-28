package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.ContentBlockData;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ContentBlockSettingsPresenter extends BasePresenter<Void> {
    public static final String SPONSOR_BLOCK_NAME = "SponsorBlock";
    public static final String SPONSOR_BLOCK_URL = "https://sponsor.ajay.app";
    public static final String SPONSOR_BLOCK_TITLE = String.format("%s (%s)", SPONSOR_BLOCK_NAME, SPONSOR_BLOCK_URL);
    private final ContentBlockData mContentBlockData;

    public ContentBlockSettingsPresenter(Context context) {
        super(context);
        mContentBlockData = ContentBlockData.instance(context);
    }

    public static ContentBlockSettingsPresenter instance(Context context) {
        return new ContentBlockSettingsPresenter(context);
    }

    public void show() {
        AppSettingsPresenter settingsPresenter = AppSettingsPresenter.instance(getContext());
        settingsPresenter.clear();
        
        appendSponsorBlockSwitch(settingsPresenter);
        appendNotificationTypeSection(settingsPresenter);
        appendCategoriesSection(settingsPresenter);

        settingsPresenter.showDialog(SPONSOR_BLOCK_NAME);
    }

    private void appendSponsorBlockSwitch(AppSettingsPresenter settingsPresenter) {
        OptionItem sponsorBlockOption = UiOptionItem.from(
                SPONSOR_BLOCK_TITLE,
                option -> mContentBlockData.setSponsorBlockEnabled(option.isSelected()),
                mContentBlockData.isSponsorBlockEnabled()
        );

        settingsPresenter.appendSingleSwitch(sponsorBlockOption);
    }

    private void appendNotificationTypeSection(AppSettingsPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        int notificationType = mContentBlockData.getNotificationType();

        for (int[] pair : new int[][] {
                {R.string.content_block_notify_none, ContentBlockData.NOTIFICATION_TYPE_NONE},
                {R.string.content_block_notify_toast, ContentBlockData.NOTIFICATION_TYPE_TOAST},
                {R.string.content_block_notify_dialog, ContentBlockData.NOTIFICATION_TYPE_DIALOG}
        }) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]),
                    optionItem -> mContentBlockData.setNotificationType(pair[1]),
                    notificationType == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.content_block_notification_type), options);
    }

    private void appendCategoriesSection(AppSettingsPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        Set<String> categories = mContentBlockData.getCategories();

        for (String[] pair : new String[][] {
                {getContext().getString(R.string.content_block_sponsor), SponsorSegment.CATEGORY_SPONSOR},
                {getContext().getString(R.string.content_block_intro), SponsorSegment.CATEGORY_INTRO},
                {getContext().getString(R.string.content_block_outro), SponsorSegment.CATEGORY_OUTRO},
                {getContext().getString(R.string.content_block_interaction), SponsorSegment.CATEGORY_INTERACTION},
                {getContext().getString(R.string.content_block_self_promo), SponsorSegment.CATEGORY_SELF_PROMO},
                {getContext().getString(R.string.content_block_music_off_topic), SponsorSegment.CATEGORY_MUSIC_OFF_TOPIC}
        }) {
            options.add(UiOptionItem.from(pair[0],
                    optionItem -> {
                        if (optionItem.isSelected()) {
                            mContentBlockData.addCategory(pair[1]);
                        } else {
                            mContentBlockData.removeCategory(pair[1]);
                        }
                    },
                    categories.contains(pair[1])));
        }

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.content_block_categories), options);
    }
}
