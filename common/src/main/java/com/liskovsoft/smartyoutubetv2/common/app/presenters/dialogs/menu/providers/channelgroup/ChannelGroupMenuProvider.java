package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.channelgroup;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.liskovsoft.mediaserviceinterfaces.data.ItemGroup;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter.VideoMenuCallback;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.ContextMenuProvider;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChannelGroupMenuProvider extends ContextMenuProvider {
    private final Context mContext;
    private final ChannelGroupServiceWrapper mService;

    public ChannelGroupMenuProvider(@NonNull Context context, int idx) {
        super(idx);
        mContext = context;
        mService = ChannelGroupServiceWrapper.instance(context);
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

        List<ItemGroup> groups = mService.getChannelGroups();

        List<OptionItem> options = new ArrayList<>();

        // Create new group or enter url
        String editDialogTitle = mContext.getString(R.string.new_subscriptions_group);
        options.add(UiOptionItem.from(editDialogTitle, optionItem -> {
            dialogPresenter.closeDialog();
            SimpleEditDialog.show(mContext, editDialogTitle,
                    null,
                    newValue -> {
                        if (mService.findChannelGroupByTitle(newValue) != null) {
                            return false;
                        }

                        if (Helpers.contains(newValue, "/") && !Helpers.startsWith(newValue, "http")) {
                            newValue = String.format("https://%s", newValue);
                        }

                        if (Helpers.startsWith(newValue, "http")) {
                            RxHelper.execute(mService.importGroupsObserve(Uri.parse(newValue)), this::pinGroups,
                                    error -> MessageHelpers.showLongMessage(mContext, error.getMessage()));
                        } else {
                            ItemGroup group = mService.createChannelGroup(newValue, null,
                                    Collections.singletonList(mService.createChannel(item.getAuthor(), item.cardImageUrl, item.channelId)));
                            mService.addChannelGroup(group);
                            BrowsePresenter.instance(mContext).pinItem(Video.from(group));
                            MessageHelpers.showMessage(mContext, mContext.getString(R.string.pinned_to_sidebar));
                        }
                        return true;
                    });
        }, false));

        //options.add(AppDialogUtil.createSubscriptionsBackupButton(mContext));

        for (ItemGroup group : groups) {
            options.add(UiOptionItem.from(group.getTitle(), optionItem -> {
                BrowsePresenter presenter = BrowsePresenter.instance(mContext);

                if (optionItem.isSelected()) {
                    group.add(mService.createChannel(item.getAuthor(), item.cardImageUrl, item.channelId));
                } else {
                    group.remove(item.channelId);
                    Object data = presenter.getCurrentSection() != null ? presenter.getCurrentSection().getData() : null;
                    if (callback != null && (data instanceof Video) && Helpers.equals(((Video) data).channelGroupId, group.getId())) {
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

    private void pinGroups(@NonNull List<ItemGroup> newGroups) {
        if (newGroups.isEmpty()) {
            return;
        }

        for (ItemGroup group : newGroups) {
            BrowsePresenter.instance(mContext).pinItem(Video.from(group));
        }
        MessageHelpers.showMessage(mContext, mContext.getString(R.string.pinned_to_sidebar));
    }
}
