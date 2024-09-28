package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.channelgroup;

import android.content.Context;

import androidx.annotation.NonNull;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter.VideoMenuCallback;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.ContextMenuProvider;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.channelgroup.ChannelGroup.Channel;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;

import java.util.ArrayList;
import java.util.List;

public class SubscriptionGroupMenuProvider extends ContextMenuProvider {
    private final Context mContext;
    private final ChannelGroupService mService;

    public SubscriptionGroupMenuProvider(@NonNull Context context, int idx) {
        super(idx);
        mContext = context;
        mService = ChannelGroupService.instance(context);
    }

    @Override
    public int getTitleResId() {
        return R.string.add_to_subscriptions_group;
    }

    @Override
    public void onClicked(Video item, VideoMenuCallback callback) {
        showGroupDialogAndFetchChannel(item, callback);
    }

    @Override
    public boolean isEnabled(Video item) {
        return item != null && (item.channelId != null || item.videoId != null);
    }

    @Override
    public int getMenuType() {
        return MENU_TYPE_VIDEO;
    }

    private void showGroupDialogAndFetchChannel(Video item, VideoMenuCallback callback) {
        if (item.hasChannel()) {
            showGroupDialog(item, callback);
        } else {
            MessageHelpers.showMessage(mContext, R.string.wait_data_loading);

            MediaServiceManager.instance().loadMetadata(item, metadata -> {
                item.sync(metadata);
                showGroupDialog(item, callback);
            });
        }
    }

    private void showGroupDialog(Video item, VideoMenuCallback callback) {
        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(mContext);

        List<ChannelGroup> groups = mService.getChannelGroups();

        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(mContext.getString(R.string.new_subscriptions_group), optionItem -> {
            dialogPresenter.closeDialog();
            SimpleEditDialog.show(mContext,
                    mContext.getString(R.string.new_subscriptions_group),
                    null,
                    newValue -> {
                        if (mService.findChannelGroup(newValue) != null) {
                            return false;
                        }

                        ChannelGroup group = new ChannelGroup(newValue, null, new Channel(item.getAuthor(), item.cardImageUrl, item.channelId));
                        mService.addChannelGroup(group);
                        BrowsePresenter.instance(mContext).pinItem(Video.from(group));
                        MessageHelpers.showMessage(mContext, mContext.getString(R.string.pinned_to_sidebar));
                        return true;
                    });
        }, false));

        for (ChannelGroup group : groups) {
            options.add(UiOptionItem.from(group.title, optionItem -> {
                BrowsePresenter presenter = BrowsePresenter.instance(mContext);

                if (optionItem.isSelected()) {
                    group.add(new Channel(item.getAuthor(), item.cardImageUrl, item.channelId));
                } else {
                    group.remove(item.channelId);
                    Object data = presenter.getCurrentSection().getData();
                    if (callback != null && (data instanceof Video) && ((Video) data).channelGroupId == group.id) {
                        callback.onItemAction(item, VideoMenuCallback.ACTION_REMOVE_AUTHOR);
                    }
                }

                if (!group.isEmpty()) {
                    mService.addChannelGroup(group);
                    presenter.pinItem(Video.from(group));
                } else {
                    mService.removeChannelGroup(group);
                    presenter.unpinItem(Video.from(group));
                }
            }, group.contains(item.channelId)));
        }

        dialogPresenter.appendCheckedCategory(mContext.getString(getTitleResId()), options);
        dialogPresenter.showDialog(mContext.getString(getTitleResId()));
    }
}
