package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import android.text.TextUtils;
import androidx.core.content.ContextCompat;
import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.ContentBlockManager.SegmentAction;
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
        appendActionTypeSection(settingsPresenter);
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

    //private void appendActionTypeSection(AppDialogPresenter settingsPresenter) {
    //    List<OptionItem> options = new ArrayList<>();
    //
    //    int notificationType = mContentBlockData.getActionType();
    //
    //    for (int[] pair : new int[][] {
    //            {R.string.content_block_action_none, ContentBlockData.ACTION_DO_NOTHING},
    //            {R.string.content_block_action_only_skip, ContentBlockData.ACTION_SKIP_ONLY},
    //            {R.string.content_block_action_toast, ContentBlockData.ACTION_SKIP_WITH_TOAST},
    //            {R.string.content_block_action_dialog, ContentBlockData.ACTION_SHOW_DIALOG}
    //    }) {
    //        options.add(UiOptionItem.from(getContext().getString(pair[0]),
    //                optionItem -> {
    //                    //mContentBlockData.setActionType(pair[1]);
    //
    //                    AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(getActivity());
    //                    dialogPresenter.clear();
    //                    dialogPresenter.appendRadioCategory(afrPauseCategory.title, afrPauseCategory.options);
    //                    dialogPresenter.showDialog(afrPauseCategory.title, mApplyAfr);
    //                },
    //                notificationType == pair[1]));
    //    }
    //
    //    settingsPresenter.appendStringsCategory(getContext().getString(R.string.content_block_action_type), options);
    //}

    private void appendActionTypeSection(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        Set<SegmentAction> actions = mContentBlockData.getActions();

        for (SegmentAction action : actions) {
            options.add(UiOptionItem.from(
                    getColoredString(mContentBlockData.getLocalizedRes(action.segmentCategory), mContentBlockData.getColorRes(action.segmentCategory)),
                    optionItem -> {
                        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(getContext());
                        dialogPresenter.clear();

                        List<OptionItem> nestedOptions = new ArrayList<>();
                        nestedOptions.add(UiOptionItem.from(getContext().getString(R.string.content_block_action_none),
                                optionItem1 -> action.actionType = ContentBlockData.ACTION_DO_NOTHING,
                                action.actionType == ContentBlockData.ACTION_DO_NOTHING));
                        nestedOptions.add(UiOptionItem.from(getContext().getString(R.string.content_block_action_only_skip),
                                optionItem1 -> action.actionType = ContentBlockData.ACTION_SKIP_ONLY,
                                action.actionType == ContentBlockData.ACTION_SKIP_ONLY));
                        nestedOptions.add(UiOptionItem.from(getContext().getString(R.string.content_block_action_toast),
                                optionItem1 -> action.actionType = ContentBlockData.ACTION_SKIP_WITH_TOAST,
                                action.actionType == ContentBlockData.ACTION_SKIP_WITH_TOAST));
                        nestedOptions.add(UiOptionItem.from(getContext().getString(R.string.content_block_action_dialog),
                                optionItem1 -> action.actionType = ContentBlockData.ACTION_SHOW_DIALOG,
                                action.actionType == ContentBlockData.ACTION_SHOW_DIALOG));

                        String title = getContext().getString(mContentBlockData.getLocalizedRes(action.segmentCategory));

                        dialogPresenter.appendRadioCategory(title, nestedOptions);
                        dialogPresenter.showDialog(title, mContentBlockData::persistActions);
                    }));
        }

        settingsPresenter.appendStringsCategory(getContext().getString(R.string.content_block_action_type), options);
    }

    private void appendCategoriesSection(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        Set<String> categories = mContentBlockData.getCategories();

        for (CharSequence[] pair : new CharSequence[][] {
                {getColoredString(R.string.content_block_sponsor, mContentBlockData.getColorRes(SponsorSegment.CATEGORY_SPONSOR)), SponsorSegment.CATEGORY_SPONSOR},
                {getColoredString(R.string.content_block_intro, mContentBlockData.getColorRes(SponsorSegment.CATEGORY_INTRO)), SponsorSegment.CATEGORY_INTRO},
                {getColoredString(R.string.content_block_outro, mContentBlockData.getColorRes(SponsorSegment.CATEGORY_OUTRO)), SponsorSegment.CATEGORY_OUTRO},
                {getColoredString(R.string.content_block_self_promo, mContentBlockData.getColorRes(SponsorSegment.CATEGORY_SELF_PROMO)), SponsorSegment.CATEGORY_SELF_PROMO},
                {getColoredString(R.string.content_block_interaction, mContentBlockData.getColorRes(SponsorSegment.CATEGORY_INTERACTION)), SponsorSegment.CATEGORY_INTERACTION},
                {getColoredString(R.string.content_block_music_off_topic, mContentBlockData.getColorRes(SponsorSegment.CATEGORY_MUSIC_OFF_TOPIC)), SponsorSegment.CATEGORY_MUSIC_OFF_TOPIC},
                {getColoredString(R.string.content_block_preview_recap, mContentBlockData.getColorRes(SponsorSegment.CATEGORY_PREVIEW_RECAP)), SponsorSegment.CATEGORY_PREVIEW_RECAP},
                {getColoredString(R.string.content_block_highlight, mContentBlockData.getColorRes(SponsorSegment.CATEGORY_HIGHLIGHT)), SponsorSegment.CATEGORY_HIGHLIGHT}
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

    private CharSequence getColoredStringOld(int strResId, int colorResId) {
        return mContentBlockData.isColorMarkersEnabled() ?
                Utils.color(getContext().getString(strResId), ContextCompat.getColor(getContext(), colorResId)) : getContext().getString(strResId);
    }

    private CharSequence getColoredString(int strResId, int colorResId) {
        String origin = getContext().getString(strResId);
        CharSequence colorMark = Utils.color("●", ContextCompat.getColor(getContext(), colorResId));
        return TextUtils.concat( colorMark, " ", origin);
    }
}
