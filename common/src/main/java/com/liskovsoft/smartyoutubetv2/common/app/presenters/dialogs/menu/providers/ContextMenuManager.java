package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.channelgroup.RemoveGroupMenuProvider;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.channelgroup.RenameGroupMenuProvider;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.channelgroup.ChannelGroupMenuProvider;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.playlist.AddChannelToPlaylistMenuProvider;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.playlist.AddPlaylistToPlaylistMenuProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ContextMenuManager {
    private final ArrayList<ContextMenuProvider> mProviders;

    public ContextMenuManager(Context context) {
        mProviders = new ArrayList<>();
        // NOTE: don't change idx after release
        mProviders.add(new ChannelGroupMenuProvider(context, 0));
        mProviders.add(new RemoveGroupMenuProvider(context, 1));
        mProviders.add(new RenameGroupMenuProvider(context, 2));
        mProviders.add(new AddPlaylistToPlaylistMenuProvider(context, 3));
        mProviders.add(new AddChannelToPlaylistMenuProvider(context, 4));
    }

    public List<ContextMenuProvider> getProviders() {
        return Collections.unmodifiableList(mProviders);
    }
}
