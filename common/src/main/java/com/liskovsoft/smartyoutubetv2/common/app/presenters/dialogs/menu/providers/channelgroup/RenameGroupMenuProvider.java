package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.channelgroup;

import android.content.Context;

import androidx.annotation.NonNull;

import com.liskovsoft.mediaserviceinterfaces.data.ItemGroup;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter.VideoMenuCallback;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.ContextMenuProvider;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;

public class RenameGroupMenuProvider extends ContextMenuProvider {
    private final Context mContext;
    private final ChannelGroupServiceWrapper mService;

    public RenameGroupMenuProvider(@NonNull Context context, int idx) {
        super(idx);
        mContext = context;
        mService = ChannelGroupServiceWrapper.instance(context);
    }

    @Override
    public int getTitleResId() {
        return R.string.rename_group;
    }

    @Override
    public void onClicked(Video item, VideoMenuCallback callback) {
        AppDialogPresenter.instance(mContext).closeDialog();
        SimpleEditDialog.show(
                mContext,
                mContext.getString(R.string.rename_group),
                item.title,
                newValue -> {
                    item.title = newValue;
                    BrowsePresenter.instance(mContext).renameSection(item);

                    ItemGroup channelGroup = mService.findChannelGroup(item.channelGroupId);

                    if (channelGroup != null) {
                        //channelGroup.title = newValue;
                        //mService.addChannelGroup(channelGroup);
                        mService.renameChannelGroup(channelGroup, newValue);
                    }

                    return true;
                });
    }

    @Override
    public boolean isEnabled(Video item) {
        return item != null && item.channelGroupId != -1;
    }

    @Override
    public int getMenuType() {
        return MENU_TYPE_SECTION;
    }
}
