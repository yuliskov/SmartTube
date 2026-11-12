package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.playlist;

import android.content.Context;

import androidx.annotation.NonNull;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter.VideoMenuCallback;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.ContextMenuProvider;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

/**
 * Adds all regular videos of the channel (no Shorts, no live streams) to a playlist of the user
 */
public class AddChannelToPlaylistMenuProvider extends ContextMenuProvider {
    public AddChannelToPlaylistMenuProvider(@NonNull Context context, int idx) {
        super(context, idx);
    }

    @Override
    public int getTitleResId() {
        return R.string.add_channel_to_playlist;
    }

    @Override
    public void onClicked(Video item, VideoMenuCallback callback) {
        if (getContext() == null || !isEnabled(item)) {
            return;
        }

        if (item.hasChannel()) {
            obtainVideoAndShowTargetDialog(item);
        } else {
            MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);

            MediaServiceManager.instance().loadMetadata(item, metadata -> {
                item.sync(metadata);
                obtainVideoAndShowTargetDialog(item);
            });
        }
    }

    @Override
    public boolean isEnabled(Video item) {
        // A video or a channel card
        return item != null && (item.hasVideo() || item.isChannel()) && !item.isPlaylistAsChannel();
    }

    @Override
    public int getMenuType() {
        return MENU_TYPE_ANY;
    }

    private void obtainVideoAndShowTargetDialog(Video item) {
        if (getContext() == null) {
            return;
        }

        if (!item.hasChannel()) {
            MessageHelpers.showMessage(getContext(), R.string.section_is_empty);
            return;
        }

        if (item.hasVideo()) {
            showTargetDialog(item, item.videoId);
            return;
        }

        // A channel card. Any video of the channel is needed to obtain the playlists.
        BulkAddToPlaylistHelper.loadFirstItem(getContext(), item, firstItem -> showTargetDialog(item, firstItem.getVideoId()));
    }

    private void showTargetDialog(Video item, String videoId) {
        if (getContext() == null) {
            return;
        }

        String channelId = item.channelId;

        BulkAddToPlaylistHelper.showTargetDialog(
                getContext(),
                getContext().getString(getTitleResId()),
                item.isChannel() ? item.getTitle() : item.getAuthor(),
                null,
                videoId,
                targetPlaylistId -> YouTubeServiceManager.instance().getMediaItemService().addChannelToPlaylistObserve(targetPlaylistId, channelId)
        );
    }
}
