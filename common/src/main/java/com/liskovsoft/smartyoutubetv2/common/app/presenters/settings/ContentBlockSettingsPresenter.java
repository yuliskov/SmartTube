package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import androidx.core.content.ContextCompat;
import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.ContentBlockData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ContentBlockSettingsPresenter extends BasePresenter<Void> {
    private static final String CONTENT_BLOCK_TITLE = String.format(
            "%s (%s)",
            ContentBlockData.SPONSOR_BLOCK_NAME,
            ContentBlockData.SPONSOR_BLOCK_URL
    );
    private final ContentBlockData mContentBlockData;

    public ContentBlockSettingsPresenter(Context context) {
        super(context);
        mContentBlockData = ContentBlockData.instance(context);
    }

    public static ContentBlockSettingsPresenter instance(Context context) {
        return new ContentBlockSettingsPresenter(context);
    }

    public void show() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());
        settingsPresenter.clear();
        
        appendSponsorBlockSwitch(settingsPresenter);
        appendNotificationTypeSection(settingsPresenter);
        appendCategoriesSection(settingsPresenter);
        appendMiscSection(settingsPresenter);

        settingsPresenter.showDialog(ContentBlockData.SPONSOR_BLOCK_NAME);
    }

    private void appendSponsorBlockSwitch(AppDialogPresenter settingsPresenter) {
        OptionItem sponsorBlockOption = UiOptionItem.from(CONTENT_BLOCK_TITLE,
                option -> mContentBlockData.enableSponsorBlock(option.isSelected()),
                mContentBlockData.isSponsorBlockEnabled()
        );

        settingsPresenter.appendSingleSwitch(sponsorBlockOption);
    }

    private void appendNotificationTypeSection(AppDialogPresenter settingsPresenter) {
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

    private void appendCategoriesSection(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        Set<String> categories = mContentBlockData.getCategories();

        for (CharSequence[] pair : new CharSequence[][] {
                {getColoredString(R.string.content_block_sponsor, R.color.green), SponsorSegment.CATEGORY_SPONSOR},
                {getColoredString(R.string.content_block_intro, R.color.cyan), SponsorSegment.CATEGORY_INTRO},
                {getColoredString(R.string.content_block_outro, R.color.blue), SponsorSegment.CATEGORY_OUTRO},
                {getColoredString(R.string.content_block_self_promo, R.color.yellow), SponsorSegment.CATEGORY_SELF_PROMO},
                {getColoredString(R.string.content_block_interaction, R.color.magenta), SponsorSegment.CATEGORY_INTERACTION},
                {getColoredString(R.string.content_block_music_off_topic, R.color.brown), SponsorSegment.CATEGORY_MUSIC_OFF_TOPIC},
                {getColoredString(R.string.content_block_preview_recap, R.color.white), SponsorSegment.CATEGORY_PREVIEW_RECAP},
                {getColoredString(R.string.content_block_highlight, R.color.red), SponsorSegment.CATEGORY_HIGHLIGHT}
        }) {
            options.add(UiOptionItem.from(pair[0],
                    optionItem -> {
                        if (optionItem.isSelected()) {
                            mContentBlockData.addCategory((String) pair[1]);
                        } else {
                            mContentBlockData.removeCategory((String) pair[1]);
                        }
                    },
                    categories.contains((String) pair[1])));
        }

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.content_block_categories), options);
    }

    private void appendMiscSection(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.skip_each_segment_once),
                optionItem -> mContentBlockData.enableSkipEachSegmentOnce(optionItem.isSelected()),
                mContentBlockData.isSkipEachSegmentOnceEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.sponsor_color_markers),
                optionItem -> mContentBlockData.enableColorMarkers(optionItem.isSelected()),
                mContentBlockData.isColorMarkersEnabled()));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.player_other), options);
    }

    private CharSequence getColoredString(int strResId, int colorResId) {
        return mContentBlockData.isColorMarkersEnabled() ?
                Utils.color(getContext().getString(strResId), ContextCompat.getColor(getContext(), colorResId)) : getContext().getString(strResId);
    }
}
