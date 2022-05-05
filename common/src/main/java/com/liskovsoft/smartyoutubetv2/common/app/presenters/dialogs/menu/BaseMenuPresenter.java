package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu;

import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.rx.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter.VideoMenuCallback;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import com.liskovsoft.youtubeapi.service.YouTubeMediaItemManager;
import io.reactivex.Observable;

import java.util.Random;

public abstract class BaseMenuPresenter extends BasePresenter<Void> {
    private final MediaServiceManager mServiceManager;

    protected BaseMenuPresenter(Context context) {
        super(context);
        mServiceManager = MediaServiceManager.instance();
    }

    protected abstract Video getVideo();
    protected abstract AppDialogPresenter getDialogPresenter();
    protected abstract VideoMenuCallback getCallback();
    protected abstract boolean isPinToSidebarEnabled();
    protected abstract boolean isSavePlaylistEnabled();
    protected abstract boolean isCreatePlaylistEnabled();
    protected abstract boolean isAccountSelectionEnabled();

    public void closeDialog() {
        if (getDialogPresenter() != null) {
            getDialogPresenter().closeDialog();
        }
        MessageHelpers.cancelToasts();
    }

    protected void appendTogglePinVideoToSidebarButton() {
        appendTogglePinPlaylistButton();
        appendTogglePinChannelButton();
    }

    private void appendTogglePinPlaylistButton() {
        if (!isPinToSidebarEnabled()) {
            return;
        }

        Video original = getVideo();

        if (original == null || !original.hasPlaylist()) {
            return;
        }

        getDialogPresenter().appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.pin_unpin_playlist),
                        optionItem -> togglePinToSidebar(createPinnedPlaylist(original))));
    }

    private void appendTogglePinChannelButton() {
        if (!isPinToSidebarEnabled()) {
            return;
        }

        Video original = getVideo();

        if (original == null || (!original.hasVideo() && !original.hasReloadPageKey() && !original.hasChannel())) {
            return;
        }

        getDialogPresenter().appendSingleButton(
                UiOptionItem.from(
                        getContext().getString(original.isChannelPlaylist() || original.belongsToPlaylists() ? R.string.pin_unpin_playlist : R.string.pin_unpin_channel),
                        optionItem -> {
                            if (original.hasVideo()) {
                                MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);

                                mServiceManager.loadMetadata(
                                        original,
                                        metadata -> {
                                            Video video = Video.from(original);
                                            video.sync(metadata);
                                            togglePinToSidebar(createPinnedChannel(video));
                                        }
                                );
                            } else {
                                togglePinToSidebar(createPinnedChannel(original));
                            }
                        }));
    }
    
    protected void togglePinToSidebar(Video section) {
        BrowsePresenter presenter = BrowsePresenter.instance(getContext());

        // Toggle between pin/unpin while dialog is opened
        boolean isItemPinned = presenter.isItemPinned(section);

        if (isItemPinned) {
            presenter.unpinItem(section);
        } else {
            presenter.pinItem(section);
        }
        MessageHelpers.showMessage(getContext(), section.title + ": " + getContext().getString(isItemPinned ? R.string.unpinned_from_sidebar : R.string.pinned_to_sidebar));
    }

    private Video createPinnedPlaylist(Video video) {
        if (video == null || !video.hasPlaylist()) {
            return null;
        }

        Video section = new Video();
        section.itemType = video.itemType;
        section.playlistId = video.playlistId;
        section.playlistParams = video.playlistParams;
        // Trying to properly format channel playlists, mixes etc
        boolean isChannelPlaylistItem = video.getGroupTitle() != null && video.belongsToSameAuthorGroup() && video.belongsToSamePlaylistGroup();
        boolean isUserPlaylistItem = video.getGroupTitle() != null && video.belongsToSamePlaylistGroup();
        String title = isChannelPlaylistItem ? video.extractAuthor() : isUserPlaylistItem ? null : video.title;
        String subtitle = isChannelPlaylistItem || isUserPlaylistItem ? video.getGroupTitle() : video.extractAuthor();
        section.title = title != null && subtitle != null ? String.format("%s - %s", title, subtitle) : String.format("%s", title != null ? title : subtitle);
        section.cardImageUrl = video.cardImageUrl;

        return section;
    }

    private Video createPinnedChannel(Video video) {
        if (video == null || (!video.hasReloadPageKey() && !video.hasChannel() && !video.isChannel())) {
            return null;
        }

        Video section = new Video();
        section.itemType = video.itemType;
        section.channelId = video.channelId;
        section.reloadPageKey = video.getReloadPageKey();
        // Trying to properly format channel playlists, mixes etc
        boolean hasChannel = video.hasChannel() && !video.isChannel();
        boolean isUserPlaylistItem = video.getGroupTitle() != null && video.belongsToSamePlaylistGroup();
        String title = hasChannel ? video.extractAuthor() : isUserPlaylistItem ? null : video.title;
        String subtitle = isUserPlaylistItem ? video.getGroupTitle() : hasChannel || video.isChannel() ? null : video.extractAuthor();
        section.title = title != null && subtitle != null ? String.format("%s - %s", title, subtitle) : String.format("%s", title != null ? title : subtitle);
        section.cardImageUrl = video.cardImageUrl;

        return section;
    }

    protected void appendAccountSelectionButton() {
        if (!isAccountSelectionEnabled()) {
            return;
        }

        getDialogPresenter().appendSingleButton(
                UiOptionItem.from(
                        getContext().getString(R.string.dialog_account_list),
                        optionItem -> AccountSelectionPresenter.instance(getContext()).show(true)
                ));
    }

    protected void appendSavePlaylistButton() {
        if (!isSavePlaylistEnabled()) {
            return;
        }

        Video original = getVideo();

        if (original == null || (!original.hasPlaylist() && !original.isChannelPlaylist() && !original.belongsToPlaylists())) {
            return;
        }

        getDialogPresenter().appendSingleButton(
                UiOptionItem.from(
                        getContext().getString(original.belongsToPlaylists()? R.string.remove_playlist : R.string.save_remove_playlist),
                        optionItem -> {
                            MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);
                            if (original.hasPlaylist()) {
                                syncToggleSavePlaylist(original, null);
                            } else if (original.belongsToPlaylists()) {
                                mServiceManager.loadChannelUploads(
                                        original,
                                        mediaGroup -> syncToggleSavePlaylist(original, mediaGroup)
                                );
                            } else {
                                mServiceManager.loadChannelPlaylist(
                                        original,
                                        mediaGroup -> syncToggleSavePlaylist(original, mediaGroup)
                                );
                            }
                        }
                ));
    }

    private void syncToggleSavePlaylist(Video original, MediaGroup mediaGroup) {
        Video video = Video.from(original);

        // Need correct playlist title to further comparison (decide whether save or remove)
        if (original.belongsToSamePlaylistGroup()) {
            video.title = original.group.getTitle();
        }

        if (!original.hasPlaylist() && mediaGroup != null && mediaGroup.getMediaItems() != null) {
            MediaItem first = mediaGroup.getMediaItems().get(0);
            video.playlistId = first.getPlaylistId();
        }

        toggleSavePlaylist(video);
    }

    private void toggleSavePlaylist(Video video) {
        mServiceManager.loadPlaylists(video, group -> {
            boolean isSaved = false;

            for (MediaItem item : group.getMediaItems()) {
                if (Helpers.equals(video.title, item.getTitle())) {
                    isSaved = true;
                    break;
                }
            }

            if (isSaved) {
                if (video.playlistId == null) {
                    MessageHelpers.showMessage(getContext(), R.string.cant_delete_empty_playlist);
                    //closeDialog();
                } else if (getVideo().belongsToPlaylists() && getCallback() != null) { // check that the parent is playlist
                    AppDialogUtil.showConfirmationDialog(getContext(), () -> {
                        removePlaylist(video);
                        getCallback().onItemAction(getVideo(), VideoMenuCallback.ACTION_REMOVE);
                        closeDialog();
                    }, video.title);
                } else {
                    removePlaylist(video);
                }
            } else {
                savePlaylist(video);
            }
        });
    }

    private void removePlaylist(Video video) {
        MediaItemManager manager = YouTubeMediaItemManager.instance();
        Observable<Void> action = manager.removePlaylistObserve(video.playlistId);
        RxUtils.execute(action, () ->
                MessageHelpers.showMessage(getContext(), video.title + ": " + getContext().getString(R.string.removed_from_playlists))
        );
    }

    private void savePlaylist(Video video) {
        MediaItemManager manager = YouTubeMediaItemManager.instance();
        Observable<Void> action = manager.savePlaylistObserve(video.playlistId);
        RxUtils.execute(action, () ->
                MessageHelpers.showMessage(getContext(), video.title + ": " + getContext().getString(R.string.saved_to_playlists))
        );
    }

    protected void appendCreatePlaylistButton() {
        if (!isCreatePlaylistEnabled()) {
            return;
        }

        Video original = getVideo() != null ? getVideo() : new Video();

        if (!original.belongsToPlaylists() && !original.hasVideo() && !BrowsePresenter.instance(getContext()).isPlaylistsSection()) {
            return;
        }

        getDialogPresenter().appendSingleButton(
                UiOptionItem.from(
                        getContext().getString(original.hasVideo() ? R.string.add_video_to_new_playlist : R.string.create_playlist),
                        optionItem -> showCreatePlaylistDialog(original)
                        ));
    }

    private void showCreatePlaylistDialog(Video video) {
        closeDialog();
        SimpleEditDialog.show(
                getContext(),
                "Playlist" + new Random().nextInt(100),
                newValue -> {
                    MediaItemManager manager = YouTubeMediaItemManager.instance();
                    Observable<Void> action = manager.createPlaylistObserve(newValue, video.hasVideo() ? video.videoId : null);
                    RxUtils.execute(action, () -> {
                        if (!video.hasVideo()) {
                            BrowsePresenter.instance(getContext()).refresh();
                        }
                    });
                },
                getContext().getString(R.string.create_playlist)
        );
    }
}
