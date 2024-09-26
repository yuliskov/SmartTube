package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers;

import android.content.Context;

import androidx.annotation.NonNull;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.ChannelGroup.Channel;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;

import java.util.ArrayList;
import java.util.List;

class SubscriptionGroupMenuProvider extends ContextMenuProvider {
    private final Context mContext;

    public SubscriptionGroupMenuProvider(@NonNull Context context, int idx) {
        super(idx);
        mContext = context;
    }

    @Override
    public int getTitleResId() {
        return R.string.add_to_subscriptions_group;
    }

    @Override
    public void onClicked(Video item) {
        showGroupDialogAndFetchChannel(item);
    }

    @Override
    public boolean isEnabled(Video item) {
        return item != null && (item.channelId != null || item.videoId != null);
    }

    @Override
    public int getMenuType() {
        return MENU_TYPE_VIDEO;
    }

    private void showGroupDialogAndFetchChannel(Video item) {
        if (item.hasChannel()) {
            showGroupDialog(item);
        } else {
            MessageHelpers.showMessage(mContext, R.string.wait_data_loading);

            MediaServiceManager.instance().loadMetadata(item, metadata -> {
                item.sync(metadata);
                showGroupDialog(item);
            });
        }
    }

    private void showGroupDialog(Video item) {
        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(mContext);

        List<ChannelGroup> groups = GeneralData.instance(mContext).getChannelGroups();

        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(mContext.getString(R.string.new_subscriptions_group), optionItem -> {
            dialogPresenter.closeDialog();
            SimpleEditDialog.show(mContext,
                    mContext.getString(R.string.new_subscriptions_group),
                    null,
                    newValue -> {
                        if (GeneralData.instance(mContext).findChannelGroup(newValue) != null) {
                            return false;
                        }

                        ChannelGroup group = new ChannelGroup(newValue, null, new Channel(item.getAuthor(), item.cardImageUrl, item.channelId));
                        GeneralData.instance(mContext).addChannelGroup(group);
                        BrowsePresenter.instance(mContext).pinItem(Video.from(group));
                        return true;
                    });
        }, false));

        for (ChannelGroup group : groups) {
            options.add(UiOptionItem.from(group.title, optionItem -> {
                if (optionItem.isSelected()) {
                    group.add(new Channel(item.getAuthor(), item.cardImageUrl, item.channelId));
                } else {
                    group.remove(item.channelId);
                }

                if (!group.isEmpty()) {
                    GeneralData.instance(mContext).addChannelGroup(group);
                    BrowsePresenter.instance(mContext).pinItem(Video.from(group));
                } else {
                    GeneralData.instance(mContext).removeChannelGroup(group);
                    BrowsePresenter.instance(mContext).unpinItem(Video.from(group));
                }
            }, group.contains(item.channelId)));
        }

        dialogPresenter.appendCheckedCategory(mContext.getString(getTitleResId()), options);
        dialogPresenter.showDialog(mContext.getString(getTitleResId()));
    }
}
