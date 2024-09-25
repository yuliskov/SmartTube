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
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;

import java.util.ArrayList;
import java.util.List;

class SubscriptionGroupMenuProvider extends ContextMenuProvider {
    private final Context mContext;

    public SubscriptionGroupMenuProvider(@NonNull Context context, int pos) {
        super(pos);
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

        List<ChannelGroup> groups = MainUIData.instance(mContext).getChannelGroups();

        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(mContext.getString(R.string.new_subscriptions_group), optionItem -> {
            dialogPresenter.closeDialog();
            SimpleEditDialog.show(mContext, mContext.getString(R.string.new_subscriptions_group), newValue -> {
                MainUIData.instance(mContext).addChannelGroup(
                        new ChannelGroup(newValue, null, new Channel(item.getAuthor(), item.cardImageUrl, item.channelId))
                );
                return true;
            }, mContext.getString(R.string.new_subscriptions_group), true);
        }, false));

        for (ChannelGroup group : groups) {
            options.add(UiOptionItem.from(group.title, optionItem -> {
                if (optionItem.isSelected()) {
                    group.add(new Channel(item.getAuthor(), item.cardImageUrl, item.channelId));
                } else {
                    group.remove(item.channelId);
                }

                if (!group.isEmpty()) {
                    MainUIData.instance(mContext).addChannelGroup(group);
                    BrowsePresenter.instance(mContext).pinItem(Video.from(group));
                } else {
                    MainUIData.instance(mContext).removeChannelGroup(group);
                    BrowsePresenter.instance(mContext).unpinItem(Video.from(group));
                }
            }, group.contains(item.channelId)));
        }

        dialogPresenter.appendCheckedCategory(mContext.getString(getTitleResId()), options);
        dialogPresenter.showDialog(mContext.getString(getTitleResId()));
    }
}
