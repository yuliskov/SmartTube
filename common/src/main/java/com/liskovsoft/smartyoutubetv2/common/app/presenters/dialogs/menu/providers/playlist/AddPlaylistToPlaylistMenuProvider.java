package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.playlist;

import android.content.Context;

import androidx.annotation.NonNull;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter.VideoMenuCallback;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.ContextMenuProvider;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

/**
 * Adds all videos of the playlist to another playlist of the user
 */
public class AddPlaylistToPlaylistMenuProvider extends ContextMenuProvider {
    public AddPlaylistToPlaylistMenuProvider(@NonNull Context context, int idx) {
        super(context, idx);
    }

    @Override
    public int getTitleResId() {
        return R.string.add_playlist_to_playlist;
    }

    @Override
    public void onClicked(Video item, VideoMenuCallback callback) {
        if (getContext() == null || !isEnabled(item)) {
            return;
        }

        if (item.hasVideo() && item.hasPlaylist()) {
            showTargetDialog(item, item.playlistId, item.videoId);
            return;
        }

        // A playlist card (no video) or a video without the playlist id
        BulkAddToPlaylistHelper.loadFirstItem(getContext(), item, firstItem -> {
            String sourcePlaylistId = item.hasPlaylist() ? item.playlistId : firstItem.getPlaylistId();

            if (sourcePlaylistId == null) {
                MessageHelpers.showMessage(getContext(), R.string.section_is_empty);
                return;
            }

            showTargetDialog(item, sourcePlaylistId, firstItem.getVideoId());
        });
    }

    @Override
    public boolean isEnabled(Video item) {
        // A video inside a loaded playlist or a playlist card
        return item != null && ((item.hasVideo() && item.belongsToSamePlaylistGroup()) ||
                item.hasPlaylist() || item.hasNestedItems() || item.isPlaylistAsChannel() || item.belongsToUserPlaylists());
    }

    @Override
    public int getMenuType() {
        return MENU_TYPE_ANY;
    }

    private void showTargetDialog(Video item, String sourcePlaylistId, String videoId) {
        if (getContext() == null) {
            return;
        }

        BulkAddToPlaylistHelper.showTargetDialog(
                getContext(),
                getContext().getString(getTitleResId()),
                getSourceTitle(item),
                sourcePlaylistId,
                videoId,
                targetPlaylistId -> YouTubeServiceManager.instance().getMediaItemService().addPlaylistToPlaylistObserve(targetPlaylistId, sourcePlaylistId)
        );
    }

    private String getSourceTitle(Video item) {
        if (item.hasVideo()) {
            return item.belongsToSamePlaylistGroup() && item.getGroup() != null ? item.getGroup().getTitle() : null;
        }

        return item.getTitle();
    }
}
