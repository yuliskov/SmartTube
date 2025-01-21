package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.channelgroup;

import android.content.Context;

import androidx.annotation.NonNull;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter.VideoMenuCallback;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.ContextMenuProvider;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;

public class RemoveGroupMenuProvider extends ContextMenuProvider {
    private final Context mContext;
    private final ChannelGroupServiceWrapper mService;

    public RemoveGroupMenuProvider(@NonNull Context context, int idx) {
        super(idx);
        mContext = context;
        mService = ChannelGroupServiceWrapper.instance(context);
    }

    @Override
    public int getTitleResId() {
        return R.string.unpin_group_from_sidebar;
    }

    @Override
    public void onClicked(Video item, VideoMenuCallback callback) {
        AppDialogUtil.showConfirmationDialog(mContext, mContext.getString(R.string.unpin_group_from_sidebar), () -> {
            mService.removeChannelGroup(
                    mService.findChannelGroupById(item.channelGroupId)
            );
            BrowsePresenter.instance(mContext).unpinItem(item);
            AppDialogPresenter.instance(mContext).closeDialog();
        });
    }

    @Override
    public boolean isEnabled(Video item) {
        return item != null && item.channelGroupId != null;
    }

    @Override
    public int getMenuType() {
        return MENU_TYPE_SECTION;
    }
}
