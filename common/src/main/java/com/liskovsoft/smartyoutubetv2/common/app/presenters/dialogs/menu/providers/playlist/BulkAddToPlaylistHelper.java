package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.playlist;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.mediaserviceinterfaces.data.PlaylistInfo;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SimpleMediaItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;

/**
 * Shared flow for the menu items that add many videos to a playlist at once:<br/>
 * pick the target playlist, confirm, run the request, report the result.
 */
public class BulkAddToPlaylistHelper {
    private static final String TAG = BulkAddToPlaylistHelper.class.getSimpleName();

    public interface AddAction {
        Observable<Void> create(String targetPlaylistId);
    }

    public interface OnFirstItem {
        void onFirstItem(MediaItem firstItem);
    }

    /**
     * Loads the videos of a card that isn't a video itself (playlist, channel) and returns the first one.<br/>
     * NOTE: don't use ChannelUploadsPresenter#obtainUploadsObservable here. It cancels the loading of the current screen.
     */
    public static void loadFirstItem(Context context, Video item, OnFirstItem onFirstItem) {
        if (context == null || item == null || onFirstItem == null) {
            return;
        }

        Observable<MediaGroup> observable;

        if (item.hasNestedItems() || item.isChannel()) {
            observable = YouTubeServiceManager.instance().getContentService()
                    .getGroupObserve(item.mediaItem != null ? item.mediaItem : SimpleMediaItem.from(item));
        } else if (item.hasReloadPageKey()) {
            observable = YouTubeServiceManager.instance().getContentService().getGroupObserve(item.getReloadPageKey());
        } else {
            observable = YouTubeServiceManager.instance().getMediaItemService()
                    .getMetadataObserve(item.videoId, item.playlistId, 0, item.playlistParams)
                    .flatMap(metadata -> {
                        MediaGroup row = findFirstRow(metadata);
                        return row != null ? Observable.just(row) : Observable.<MediaGroup>empty();
                    });
        }

        MessageHelpers.showMessage(context, R.string.wait_data_loading);

        boolean[] isFound = {false};

        RxHelper.execute(
                observable,
                mediaGroup -> {
                    MediaItem firstItem = findFirstVideo(mediaGroup);

                    if (firstItem != null && !isFound[0]) {
                        isFound[0] = true;
                        onFirstItem.onFirstItem(firstItem);
                    }
                },
                error -> {
                    Log.e(TAG, "Load videos error: %s", error.getMessage());
                    MessageHelpers.showMessage(context, R.string.section_is_empty);
                },
                () -> {
                    if (!isFound[0]) {
                        MessageHelpers.showMessage(context, R.string.section_is_empty);
                    }
                }
        );
    }

    private static MediaGroup findFirstRow(MediaItemMetadata metadata) {
        if (metadata == null || metadata.getSuggestions() == null) {
            return null;
        }

        for (MediaGroup group : metadata.getSuggestions()) {
            if (group != null && group.getMediaItems() != null && !group.getMediaItems().isEmpty()) {
                return group;
            }
        }

        return null;
    }

    private static MediaItem findFirstVideo(MediaGroup mediaGroup) {
        if (mediaGroup == null || mediaGroup.getMediaItems() == null) {
            return null;
        }

        for (MediaItem mediaItem : mediaGroup.getMediaItems()) {
            if (mediaItem != null && mediaItem.getVideoId() != null) {
                return mediaItem;
            }
        }

        return null;
    }

    /**
     * @param dialogTitle title of the playlist picker
     * @param sourceTitle name of the source (playlist or channel). Used in the confirmation. May be null.
     * @param sourcePlaylistId playlist that should be hidden from the picker. May be null.
     * @param videoId any existing video. The playlists request doesn't work without it.
     * @param addAction creates the request for the chosen playlist
     */
    public static void showTargetDialog(Context context, String dialogTitle, String sourceTitle, String sourcePlaylistId, String videoId,
                                        AddAction addAction) {
        if (context == null || addAction == null) {
            return;
        }

        MediaServiceManager.instance().authCheck(
                () -> loadPlaylists(context, dialogTitle, sourceTitle, sourcePlaylistId, videoId, addAction),
                () -> MessageHelpers.showMessage(context, R.string.msg_signed_users_only)
        );
    }

    private static void loadPlaylists(Context context, String dialogTitle, String sourceTitle, String sourcePlaylistId, String videoId,
                                      AddAction addAction) {
        RxHelper.execute(
                YouTubeServiceManager.instance().getMediaItemService().getPlaylistsInfoObserve(videoId),
                playlistInfos -> showPlaylists(context, dialogTitle, sourceTitle, sourcePlaylistId, addAction, playlistInfos),
                error -> {
                    Log.e(TAG, "Get playlists error: %s", error.getMessage());
                    MessageHelpers.showLongMessage(context, error.getMessage());
                }
        );
    }

    private static void showPlaylists(Context context, String dialogTitle, String sourceTitle, String sourcePlaylistId,
                                      AddAction addAction, List<PlaylistInfo> playlistInfos) {
        List<OptionItem> options = new ArrayList<>();

        if (playlistInfos != null) {
            for (PlaylistInfo playlistInfo : playlistInfos) {
                String targetPlaylistId = playlistInfo.getPlaylistId();
                String targetTitle = playlistInfo.getTitle();

                // A playlist can't be added to itself
                if (targetPlaylistId == null || Helpers.equals(targetPlaylistId, sourcePlaylistId)) {
                    continue;
                }

                options.add(UiOptionItem.from(targetTitle,
                        optionItem -> confirmAndAdd(context, sourceTitle, targetTitle, targetPlaylistId, addAction)));
            }
        }

        if (options.isEmpty()) {
            MessageHelpers.showMessage(context, R.string.section_is_empty);
            return;
        }

        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(context);
        dialogPresenter.appendStringsCategory(dialogTitle, options);
        dialogPresenter.showDialog(dialogTitle);
    }

    private static void confirmAndAdd(Context context, String sourceTitle, String targetTitle, String targetPlaylistId, AddAction addAction) {
        String confirmTitle = sourceTitle != null ?
                context.getString(R.string.add_all_videos_confirm, sourceTitle, targetTitle) : context.getString(R.string.dialog_add_to, targetTitle);

        AppDialogUtil.showConfirmationDialog(context, confirmTitle, () -> {
            AppDialogPresenter.instance(context).closeDialog();
            add(context, targetTitle, targetPlaylistId, addAction);
        });
    }

    private static void add(Context context, String targetTitle, String targetPlaylistId, AddAction addAction) {
        String doneMessage = context.getString(R.string.added_to, targetTitle);

        MessageHelpers.showMessage(context, R.string.adding_videos);

        // NOTE: don't dispose. The request should outlive the dialog.
        RxHelper.execute(
                addAction.create(targetPlaylistId),
                error -> {
                    Log.e(TAG, "Bulk add to playlist error: %s", error.getMessage());
                    MessageHelpers.showLongMessage(context, error.getMessage());
                },
                () -> MessageHelpers.showMessage(context, doneMessage)
        );
    }
}
