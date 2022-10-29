package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu;

import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.PlaylistInfo;
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
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import com.liskovsoft.youtubeapi.service.YouTubeMediaItemService;
import io.reactivex.Observable;

import java.util.List;

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
    protected abstract boolean isAddToNewPlaylistEnabled();
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
                        getContext().getString(original.isPlaylistAsChannel() || (original.hasNestedItems() && original.belongsToUserPlaylists()) ? R.string.pin_unpin_playlist : R.string.pin_unpin_channel),
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
        String title = isChannelPlaylistItem ? video.getAuthor() : isUserPlaylistItem ? null : video.title;
        String subtitle = isChannelPlaylistItem || isUserPlaylistItem ? video.getGroupTitle() : video.getAuthor();
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
        String title = hasChannel ? video.getAuthor() : isUserPlaylistItem ? null : video.title;
        String subtitle = isUserPlaylistItem ? video.getGroupTitle() : hasChannel || video.isChannel() ? null : video.getAuthor();
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

    protected void appendSaveRemovePlaylistButton() {
        if (!isSavePlaylistEnabled()) {
            return;
        }

        Video original = getVideo();

        if (original == null || (!original.hasPlaylist() && !original.isPlaylistAsChannel() && !original.belongsToUserPlaylists())) {
            return;
        }

        // Allow removing user playlist only from Playlists section to prevent accidental deletion
        if (BrowsePresenter.instance(getContext()).isPlaylistsSection() && !BrowsePresenter.instance(getContext()).inForeground()) {
            return;
        }

        getDialogPresenter().appendSingleButton(
                UiOptionItem.from(
                        getContext().getString(original.belongsToUserPlaylists()? R.string.remove_playlist : R.string.save_remove_playlist),
                        optionItem -> {
                            MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);
                            if (original.hasPlaylist()) {
                                syncToggleSaveRemovePlaylist(original, null);
                            } else if (original.belongsToUserPlaylists()) {
                                // NOTE: Can't get empty playlist id. Empty playlist doesn't contain videos.
                                mServiceManager.loadChannelUploads(
                                        original,
                                        mediaGroup -> {
                                            String playlistId = getFirstPlaylistId(mediaGroup);

                                            if (playlistId != null) {
                                                syncToggleSaveRemovePlaylist(original, playlistId);
                                            } else {
                                                // Empty playlist fix. Get 'add to playlist' infos
                                                mServiceManager.getPlaylistInfos(playlistInfos -> {
                                                    List<PlaylistInfo> infos = Helpers.filter(playlistInfos, value -> Helpers.equals(
                                                            value.getTitle(), original.getTitle()));

                                                    String playlistId2 = null;

                                                    // Multiple playlists may share same name
                                                    if (infos != null && infos.size() == 1) {
                                                        playlistId2 = infos.get(0).getPlaylistId();
                                                    }

                                                    syncToggleSaveRemovePlaylist(original, playlistId2);
                                                });
                                            }
                                        }
                                );
                            } else {
                                mServiceManager.loadChannelPlaylist(
                                        original,
                                        mediaGroup -> syncToggleSaveRemovePlaylist(original, getFirstPlaylistId(mediaGroup))
                                );
                            }
                        }
                ));
    }

    private void syncToggleSaveRemovePlaylist(Video original, String playlistId) {
        Video video = Video.from(original);

        // Need correct playlist title to further comparison (decide whether save or remove)
        if (original.belongsToSamePlaylistGroup()) {
            video.title = original.group.getTitle();
        }

        if (!original.hasPlaylist() && playlistId != null) {
            video.playlistId = playlistId;
        }

        toggleSaveRemovePlaylist(video);
    }

    private void toggleSaveRemovePlaylist(Video video) {
        mServiceManager.loadPlaylists(video, group -> {
            boolean isSaved = false;

            for (MediaItem playlist : group.getMediaItems()) {
                if (playlist.getTitle().contains(video.title)) {
                    isSaved = true;
                    break;
                }
            }

            if (isSaved) {
                if (video.playlistId == null) {
                    MessageHelpers.showMessage(getContext(), R.string.cant_delete_empty_playlist);
                } else {
                    AppDialogUtil.showConfirmationDialog(getContext(), () -> {
                        removePlaylist(video);
                        if (getCallback() != null) {
                            getCallback().onItemAction(getVideo(), VideoMenuCallback.ACTION_REMOVE);
                            closeDialog();
                        }
                    }, String.format("%s: %s", video.title, getContext().getString(R.string.remove_playlist)));
                }
            } else {
                savePlaylist(video);
            }
        });
    }

    private void removePlaylist(Video video) {
        MediaItemService manager = YouTubeMediaItemService.instance();
        Observable<Void> action = manager.removePlaylistObserve(video.playlistId);
        GeneralData.instance(getContext()).setPlaylistOrder(video.playlistId, -1);
        RxUtils.execute(action,
                () -> MessageHelpers.showMessage(getContext(), video.title + ": " + getContext().getString(R.string.cant_delete_empty_playlist)),
                () -> MessageHelpers.showMessage(getContext(), video.title + ": " + getContext().getString(R.string.removed_from_playlists))
        );
    }

    private void savePlaylist(Video video) {
        MediaItemService manager = YouTubeMediaItemService.instance();
        Observable<Void> action = manager.savePlaylistObserve(video.playlistId);
        RxUtils.execute(action,
                () -> MessageHelpers.showMessage(getContext(), video.title + ": " + getContext().getString(R.string.cant_save_playlist)),
                () -> MessageHelpers.showMessage(getContext(), video.title + ": " + getContext().getString(R.string.saved_to_playlists))
        );
    }

    private String getFirstPlaylistId(MediaGroup mediaGroup) {
        if (mediaGroup != null && mediaGroup.getMediaItems() != null) {
            MediaItem first = mediaGroup.getMediaItems().get(0);
            return first.getPlaylistId();
        }

        return null;
    }

    protected void appendCreatePlaylistButton() {
        if (!isCreatePlaylistEnabled()) {
            return;
        }

        Video original = getVideo() != null ? getVideo() : new Video();

        if (!BrowsePresenter.instance(getContext()).isPlaylistsSectionActive() || original.hasVideo()) {
            return;
        }

        getDialogPresenter().appendSingleButton(
                UiOptionItem.from(
                        getContext().getString(R.string.create_playlist),
                        optionItem -> showCreatePlaylistDialog(original)
                        ));
    }

    protected void appendAddToNewPlaylistButton() {
        if (!isAddToNewPlaylistEnabled()) {
            return;
        }

        Video original = getVideo() != null ? getVideo() : new Video();

        if (!original.hasVideo()) {
            return;
        }

        getDialogPresenter().appendSingleButton(
                UiOptionItem.from(
                        getContext().getString(R.string.add_video_to_new_playlist),
                        optionItem -> showCreatePlaylistDialog(original)
                ));
    }

    private void showCreatePlaylistDialog(Video video) {
        closeDialog();
        SimpleEditDialog.show(
                getContext(),
                "",
                newValue -> {
                    MediaItemService manager = YouTubeMediaItemService.instance();
                    Observable<Void> action = manager.createPlaylistObserve(newValue, video.hasVideo() ? video.videoId : null);
                    RxUtils.execute(
                            action,
                            () -> MessageHelpers.showMessage(getContext(), newValue + ": " + getContext().getString(R.string.cant_save_playlist)),
                            () -> {
                                if (!video.hasVideo()) { // Playlists section
                                    BrowsePresenter.instance(getContext()).refresh();
                                } else {
                                    MessageHelpers.showMessage(getContext(), newValue + ": " + getContext().getString(R.string.saved_to_playlists));
                                }
                            }
                    );
                },
                getContext().getString(R.string.create_playlist),
                true
        );
    }

    protected void appendRenamePlaylistButton() {
        if (!isCreatePlaylistEnabled()) {
            return;
        }

        Video original = getVideo();

        if (original == null || !BrowsePresenter.instance(getContext()).isPlaylistsSectionActive()) {
            return;
        }

        getDialogPresenter().appendSingleButton(
                UiOptionItem.from(
                        getContext().getString(R.string.rename_playlist),
                        optionItem -> showRenamePlaylistDialog(original)
                ));
    }

    private void showRenamePlaylistDialog(Video video) {
        MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);
        mServiceManager.loadChannelUploads(
                video,
                mediaGroup -> {
                    closeDialog();

                    if (mediaGroup == null) { // crash fix
                        return;
                    }

                    MediaItem firstItem = mediaGroup.getMediaItems().get(0);

                    if (firstItem.getPlaylistId() == null) {
                        MessageHelpers.showMessage(getContext(), R.string.cant_rename_empty_playlist);
                        return;
                    }

                    SimpleEditDialog.show(
                            getContext(),
                            video.title,
                            newValue -> {
                                MediaItemService manager = YouTubeMediaItemService.instance();
                                Observable<Void> action = manager.renamePlaylistObserve(firstItem.getPlaylistId(), newValue);
                                RxUtils.execute(
                                        action,
                                        () -> MessageHelpers.showMessage(getContext(), R.string.owned_playlist_warning),
                                        () -> {
                                            video.title = newValue;
                                            BrowsePresenter.instance(getContext()).syncItem(video);
                                        }
                                );
                            },
                            getContext().getString(R.string.rename_playlist),
                            true
                    );
                }
        );
    }
}
