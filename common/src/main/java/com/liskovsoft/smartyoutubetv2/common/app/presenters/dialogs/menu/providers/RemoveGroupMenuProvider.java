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
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;

import java.util.ArrayList;
import java.util.List;

class RemoveGroupMenuProvider extends ContextMenuProvider {
    private final Context mContext;

    public RemoveGroupMenuProvider(@NonNull Context context, int idx) {
        super(idx);
        mContext = context;
    }

    @Override
    public int getTitleResId() {
        return R.string.unpin_group_from_sidebar;
    }

    @Override
    public void onClicked(Video item) {
        AppDialogUtil.showConfirmationDialog(mContext, mContext.getString(R.string.unpin_group_from_sidebar), () -> {
            GeneralData.instance(mContext).removeChannelGroup(
                    GeneralData.instance(mContext).findChannelGroup(item.channelGroupId)
            );
            BrowsePresenter.instance(mContext).unpinItem(item);
            AppDialogPresenter.instance(mContext).closeDialog();
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
