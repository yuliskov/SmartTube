package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers;

import android.content.Context;

import androidx.annotation.NonNull;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

class SubscriptionGroupMenuProvider extends ContextMenuProvider {
    private final Context mContext;

    public SubscriptionGroupMenuProvider(@NonNull Context context, int pos) {
        super(pos);
        mContext = context.getApplicationContext();
    }

    @Override
    public int getTitleResId() {
        return R.string.add_to_subscriptions_group;
    }

    @Override
    public void onClicked() {
        // Show dialog
        // - New group
        // - Group name 1
        // - Group name 2

        // class SubscriptionGroup(groupName, groupIcon, list of channel ids)
    }

    @Override
    public boolean isEnabled(Video item) {
        return item != null && item.channelId != null;
    }
}
