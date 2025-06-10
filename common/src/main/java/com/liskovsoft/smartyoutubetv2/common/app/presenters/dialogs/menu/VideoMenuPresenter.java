package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu;

import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.mediaserviceinterfaces.data.PlaylistInfo;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.CommentsController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerUI;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService.State;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelUploadsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.ContextMenuManager;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.ContextMenuProvider;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.misc.StreamReminderService;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VideoMenuPresenter extends BaseMenuPresenter {
    private static final String TAG = VideoMenuPresenter.class.getSimpleName();
    private final MediaItemService mMediaItemService;
    private final AppDialogPresenter mDialogPresenter;
    private final MediaServiceManager mServiceManager;
    private Disposable mAddToPlaylistAction;
    private Disposable mNotInterestedAction;
    private Disposable mSubscribeAction;
    private Disposable mPlaylistsInfoAction;
    private Video mVideo;
    public static WeakReference<Video> sVideoHolder = new WeakReference<>(null);
    private boolean mIsNotInterestedButtonEnabled;
    private boolean mIsNotRecommendChannelEnabled;
    private boolean mIsRemoveFromHistoryButtonEnabled;
    private boolean mIsRemoveFromSubscriptionsButtonEnabled;
    private boolean mIsOpenChannelButtonEnabled;
    private boolean mIsOpenChannelUploadsButtonEnabled;
    private boolean mIsSubscribeButtonEnabled;
    private boolean mIsShareLinkButtonEnabled;
    private boolean mIsShareQRLinkButtonEnabled;
    private boolean mIsShareEmbedLinkButtonEnabled;
    private boolean mIsAddToPlaylistButtonEnabled;
    private boolean mIsAddToRecentPlaylistButtonEnabled;
    private boolean mIsReturnToBackgroundVideoEnabled;
    private boolean mIsOpenPlaylistButtonEnabled;
    private boolean mIsAddToPlaybackQueueButtonEnabled;
    private boolean mIsPlayNextButtonEnabled;
    private boolean mIsShowPlaybackQueueButtonEnabled;
    private boolean mIsOpenDescriptionButtonEnabled;
    private boolean mIsOpenCommentsButtonEnabled;
    private boolean mIsPlayVideoButtonEnabled;
    private boolean mIsPlayVideoIncognitoButtonEnabled;
    private boolean mIsPlaylistOrderButtonEnabled;
    private boolean mIsStreamReminderButtonEnabled;
    private boolean mIsMarkAsWatchedButtonEnabled;
    private VideoMenuCallback mCallback;
    private List<PlaylistInfo> mPlaylistInfos;
    private final Map<Long, MenuAction> mMenuMapping = new HashMap<>();

    public interface VideoMenuCallback {
        int ACTION_UNDEFINED = 0;
        int ACTION_UNSUBSCRIBE = 1;
        int ACTION_REMOVE = 2;
        int ACTION_REMOVE_FROM_PLAYLIST = 3;
        int ACTION_REMOVE_FROM_QUEUE = 4;
        int ACTION_ADD_TO_QUEUE = 5;
        int ACTION_PLAY_NEXT = 6;
        int ACTION_REMOVE_AUTHOR = 7;
        void onItemAction(Video videoItem, int action);
    }

    public static class MenuAction {
        private final Runnable mAction;
        private final boolean mIsAuth;

        public MenuAction(Runnable action, boolean isAuth) {
            this.mAction = action;
            this.mIsAuth = isAuth;
        }

        public void run() {
            mAction.run();
        }

        public boolean isAuth() {
            return mIsAuth;
        }
    }

    private VideoMenuPresenter(Context context) {
        super(context);
        ServiceManager service = YouTubeServiceManager.instance();
        mMediaItemService = service.getMediaItemService();
        mServiceManager = MediaServiceManager.instance();
        mDialogPresenter = AppDialogPresenter.instance(context);

        initMenuMapping();
    }

    public static VideoMenuPresenter instance(Context context) {
        return new VideoMenuPresenter(context);
    }

    @Override
    protected Video getVideo() {
        return mVideo;
    }

    @Override
    protected AppDialogPresenter getDialogPresenter() {
        return mDialogPresenter;
    }

    @Override
    protected VideoMenuCallback getCallback() {
        return mCallback;
    }

    public void showMenu(Video video, VideoMenuCallback callback) {
        mCallback = callback;
        showMenu(video);
    }

    public void showMenu(Video video) {
        if (video == null) {
            return;
        }

        mVideo = video;
        sVideoHolder = new WeakReference<>(video);

        MediaServiceManager.instance().authCheck(this::bootstrapPrepareAndShowDialogSigned, this::prepareAndShowDialogUnsigned);
    }

    private void bootstrapPrepareAndShowDialogSigned() {
        mPlaylistInfos = null;
        RxHelper.disposeActions(mPlaylistsInfoAction);
        if (isAddToRecentPlaylistButtonEnabled()) {
            mPlaylistsInfoAction = mMediaItemService.getPlaylistsInfoObserve(mVideo.videoId)
                    .subscribe(
                            videoPlaylistInfos -> {
                                mPlaylistInfos = videoPlaylistInfos;
                                prepareAndShowDialogSigned();
                            },
                            error -> Log.e(TAG, "Add to recent playlist error: %s", error.getMessage())
                    );
        } else {
            prepareAndShowDialogSigned();
        }
    }

    private void prepareAndShowDialogSigned() {
        if (getContext() == null) {
            return;
        }

        appendReturnToBackgroundVideoButton();

        for (Long menuItem : MainUIData.instance(getContext()).getMenuItemsOrdered()) {
            MenuAction menuAction = mMenuMapping.get(menuItem);
            if (menuAction != null) {
                menuAction.run();
            }
        }

        if (!mDialogPresenter.isEmpty()) {
            String title = mVideo != null ? mVideo.getTitle() : null;
            // No need to add author because: 1) This could be a channel card. 2) This info isn't so important.
            mDialogPresenter.showDialog(title);
        }
    }

    private void prepareAndShowDialogUnsigned() {
        if (getContext() == null) {
            return;
        }

        appendReturnToBackgroundVideoButton();

        for (Long menuItem : MainUIData.instance(getContext()).getMenuItemsOrdered()) {
            MenuAction menuAction = mMenuMapping.get(menuItem);
            if (menuAction != null && !menuAction.isAuth()) {
                menuAction.run();
            }
        }

        if (!mDialogPresenter.isEmpty()) {
            String title = mVideo != null ? mVideo.getTitle() : null;
            mDialogPresenter.showDialog(title);
        }
    }

    private void appendAddToPlaylistButton() {
        if (!mIsAddToPlaylistButtonEnabled) {
            return;
        }

        if (mVideo == null || !mVideo.hasVideo() || mVideo.isPlaylistAsChannel()) {
            return;
        }

        getDialogPresenter().appendSingleButton(
                UiOptionItem.from(
                        getContext().getString(R.string.dialog_add_to_playlist),
                        optionItem -> AppDialogUtil.showAddToPlaylistDialog(getContext(), mVideo, mCallback)
                ));
    }

    private void appendAddToRecentPlaylistButton() {
        if (!isAddToRecentPlaylistButtonEnabled()) {
            return;
        }

        String playlistId = GeneralData.instance(getContext()).getLastPlaylistId();
        String playlistTitle = GeneralData.instance(getContext()).getLastPlaylistTitle();

        if (playlistId == null || playlistTitle == null) {
            return;
        }

        appendSimpleAddToRecentPlaylistButton(playlistId, playlistTitle);
    }

    private boolean isAddToRecentPlaylistButtonEnabled() {
        return mIsAddToPlaylistButtonEnabled && mIsAddToRecentPlaylistButtonEnabled && mVideo != null && mVideo.hasVideo();
    }

    private void appendSimpleAddToRecentPlaylistButton(String playlistId, String playlistTitle) {
        if (mPlaylistInfos == null) {
            return;
        }

        boolean isSelected = false;
        for (PlaylistInfo playlistInfo : mPlaylistInfos) {
            if (playlistInfo.getPlaylistId().equals(playlistId)) {
                isSelected = playlistInfo.isSelected();
                break;
            }
        }
        boolean add = !isSelected;
        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(
                        add ? R.string.dialog_add_to : R.string.dialog_remove_from, playlistTitle),
                        optionItem -> addRemoveFromPlaylist(playlistId, playlistTitle, add)
                )
        );
    }

    //private void appendReactiveAddToRecentPlaylistButton(String playlistId, String playlistTitle) {
    //    mDialogPresenter.appendSingleButton(
    //            UiOptionItem.from(getContext().getString(
    //                    R.string.dialog_add_remove_from, playlistTitle),
    //                    optionItem -> {
    //                        mPlaylistsInfoAction = mItemManager.getVideoPlaylistsInfoObserve(mVideo.videoId)
    //                                .subscribe(
    //                                        videoPlaylistInfos -> {
    //                                            for (VideoPlaylistInfo playlistInfo : videoPlaylistInfos) {
    //                                                if (playlistInfo.getPlaylistId().equals(playlistId)) {
    //                                                    addRemoveFromPlaylist(playlistInfo.getPlaylistId(), playlistInfo.getTitle(), !playlistInfo.isSelected());
    //                                                    break;
    //                                                }
    //                                            }
    //                                        },
    //                                        error -> {
    //                                            // Fallback to something on error
    //                                            Log.e(TAG, "Add to recent playlist error: %s", error.getMessage());
    //                                        }
    //                                );
    //                    }
    //            )
    //    );
    //}

    private void appendOpenChannelButton() {
        if (!mIsOpenChannelButtonEnabled) {
            return;
        }

        if (!ChannelPresenter.canOpenChannel(mVideo)) {
            return;
        }

        // Prepare to special type of channels that work as playlist
        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(
                        mVideo.isPlaylistAsChannel() ? R.string.open_playlist : R.string.open_channel), optionItem -> {
                    MediaServiceManager.chooseChannelPresenter(getContext(), mVideo);
                    mDialogPresenter.closeDialog();
                }));
    }

    private void appendOpenPlaylistButton() {
        if (!mIsOpenPlaylistButtonEnabled) {
            return;
        }

        // Check view to allow open playlist in grid
        if (mVideo == null || !mVideo.hasPlaylist() || (mVideo.belongsToSamePlaylistGroup() && getViewManager().getTopView() == ChannelUploadsView.class)) {
            return;
        }

        // Prepare to special type of channels that work as playlist
        if (mVideo.isPlaylistAsChannel() && ChannelPresenter.canOpenChannel(mVideo)) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.open_playlist), optionItem -> ChannelUploadsPresenter.instance(getContext()).openChannel(mVideo)));
    }

    private void appendOpenChannelUploadsButton() {
        if (!mIsOpenChannelUploadsButtonEnabled || mVideo == null) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.open_channel_uploads), optionItem -> ChannelUploadsPresenter.instance(getContext()).openChannel(mVideo)));
    }

    private void appendNotInterestedButton() {
        if (mVideo == null || mVideo.mediaItem == null || mVideo.mediaItem.getFeedbackToken() == null) {
            return;
        }

        if ((!mVideo.belongsToHome() && !mVideo.belongsToShorts()) || !mIsNotInterestedButtonEnabled) {
            return;
        }

        RxHelper.disposeActions(mNotInterestedAction);

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.not_interested), optionItem -> {
                    mNotInterestedAction = mMediaItemService.markAsNotInterestedObserve(mVideo.mediaItem.getFeedbackToken())
                            .subscribe(
                                    var -> {},
                                    error -> Log.e(TAG, "Mark as 'not interested' error: %s", error.getMessage()),
                                    () -> {
                                        if (mCallback != null) {
                                            mCallback.onItemAction(mVideo, VideoMenuCallback.ACTION_REMOVE);
                                        } else {
                                            MessageHelpers.showMessage(getContext(), R.string.you_wont_see_this_video);
                                        }
                                    }
                            );
                    mDialogPresenter.closeDialog();
                }));
    }

    private void appendNotRecommendChannelButton() {
        if (mVideo == null || mVideo.mediaItem == null || mVideo.mediaItem.getFeedbackToken2() == null) {
            return;
        }

        if ((!mVideo.belongsToHome() && !mVideo.belongsToShorts()) || !mIsNotRecommendChannelEnabled) {
            return;
        }

        RxHelper.disposeActions(mNotInterestedAction);

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.not_recommend_channel), optionItem -> {
                    mNotInterestedAction = mMediaItemService.markAsNotInterestedObserve(mVideo.mediaItem.getFeedbackToken2())
                            .subscribe(
                                    var -> {},
                                    error -> Log.e(TAG, "Mark as 'not interested' error: %s", error.getMessage()),
                                    () -> {
                                        if (mCallback != null) {
                                            mCallback.onItemAction(mVideo, VideoMenuCallback.ACTION_REMOVE);
                                        } else {
                                            MessageHelpers.showMessage(getContext(), R.string.you_wont_see_this_video);
                                        }
                                    }
                            );
                    mDialogPresenter.closeDialog();
                }));
    }

    private void appendRemoveFromHistoryButton() {
        if (mVideo == null || !mVideo.belongsToHistory() || !mIsRemoveFromHistoryButtonEnabled) {
            return;
        }

        RxHelper.disposeActions(mNotInterestedAction);

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.remove_from_history), optionItem -> {
                    if (mVideo.mediaItem == null || mVideo.mediaItem.getFeedbackToken() == null) {
                        onRemoveFromHistoryDone();
                    } else {
                        mNotInterestedAction = mMediaItemService.markAsNotInterestedObserve(mVideo.mediaItem.getFeedbackToken())
                                .subscribe(
                                        var -> {},
                                        error -> Log.e(TAG, "Remove from history error: %s", error.getMessage()),
                                        this::onRemoveFromHistoryDone
                                );
                    }
                    mDialogPresenter.closeDialog();
                }));
    }

    private void onRemoveFromHistoryDone() {
        if (mCallback != null) {
            mCallback.onItemAction(mVideo, VideoMenuCallback.ACTION_REMOVE);
        } else {
            MessageHelpers.showMessage(getContext(), R.string.removed_from_history);
        }
        VideoStateService stateService = VideoStateService.instance(getContext());
        stateService.removeByVideoId(mVideo.videoId);
        stateService.persistState();
    }

    private void appendRemoveFromSubscriptionsButton() {
        if (mVideo == null || mVideo.mediaItem == null || mVideo.mediaItem.getFeedbackToken() == null) {
            return;
        }

        if (!mVideo.belongsToSubscriptions() || !mIsRemoveFromSubscriptionsButtonEnabled) {
            return;
        }

        RxHelper.disposeActions(mNotInterestedAction);

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.remove_from_subscriptions), optionItem -> {
                    mNotInterestedAction = mMediaItemService.markAsNotInterestedObserve(mVideo.mediaItem.getFeedbackToken())
                            .subscribe(
                                    var -> {},
                                    error -> Log.e(TAG, "Remove from subscriptions error: %s", error.getMessage()),
                                    () -> {
                                        if (mCallback != null) {
                                            mCallback.onItemAction(mVideo, VideoMenuCallback.ACTION_REMOVE);
                                        }
                                    }
                            );
                    mDialogPresenter.closeDialog();
                }));
    }

    private void appendRemoveFromNotificationsButton() {
        if (mVideo == null || mVideo.mediaItem == null) {
            return;
        }

        if (!mVideo.belongsToNotifications() || !mIsRemoveFromSubscriptionsButtonEnabled) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.remove_from_subscriptions), optionItem -> {
                    MediaServiceManager.instance().hideNotification(mVideo);
                    if (mCallback != null) {
                        mCallback.onItemAction(mVideo, VideoMenuCallback.ACTION_REMOVE);
                    }
                    mDialogPresenter.closeDialog();
                }));
    }

    private void appendMarkAsWatchedButton() {
        if (mVideo == null || !mVideo.hasVideo() || !mIsMarkAsWatchedButtonEnabled) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.mark_as_watched), optionItem -> {
                    MediaServiceManager.instance().updateHistory(mVideo, 0);
                    mVideo.markFullyViewed();
                    VideoStateService.instance(getContext()).save(new State(mVideo, mVideo.getDurationMs()));
                    Playlist.instance().sync(mVideo);
                    mDialogPresenter.closeDialog();
                }));
    }

    private void appendShareLinkButton() {
        if (!mIsShareLinkButtonEnabled) {
            return;
        }

        AppDialogUtil.appendShareLinkDialogItem(getContext(), mDialogPresenter, mVideo);
    }

    private void appendShareQRLinkButton() {
        if (!mIsShareQRLinkButtonEnabled) {
            return;
        }

        AppDialogUtil.appendShareQRLinkDialogItem(getContext(), mDialogPresenter, mVideo);
    }

    private void appendShareEmbedLinkButton() {
        if (!mIsShareEmbedLinkButtonEnabled) {
            return;
        }

        AppDialogUtil.appendShareEmbedLinkDialogItem(getContext(), mDialogPresenter, mVideo);
    }

    private void appendOpenDescriptionButton() {
        if (!mIsOpenDescriptionButtonEnabled || mVideo == null) {
            return;
        }

        if (mVideo.videoId == null) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.action_video_info),
                        optionItem -> {
                            MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);
                            mServiceManager.loadMetadata(mVideo, metadata -> {
                                String description = metadata.getDescription();
                                if (description != null) {
                                    showLongTextDialog(description);
                                } else {
                                    mServiceManager.loadFormatInfo(mVideo, formatInfo -> {
                                        String newDescription = formatInfo.getDescription();
                                        if (newDescription != null) {
                                            showLongTextDialog(newDescription);
                                        } else {
                                            MessageHelpers.showMessage(getContext(), R.string.description_not_found);
                                        }
                                    });
                                }
                            });
                        }
                ));
    }

    private void appendOpenCommentsButton() {
        if (!mIsOpenCommentsButtonEnabled || mVideo == null) {
            return;
        }

        if (mVideo.videoId == null || mVideo.isLive || mVideo.isUpcoming) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.open_comments),
                        optionItem -> {
                            MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);
                            mServiceManager.loadMetadata(mVideo, metadata -> {
                                CommentsController controller = new CommentsController(getContext(), metadata);
                                controller.onButtonClicked(R.id.action_chat, PlayerUI.BUTTON_ON);
                            });
                        }
                ));
    }

    private void appendPlayVideoButton() {
        if (!mIsPlayVideoButtonEnabled || mVideo == null || mVideo.videoId == null) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.play_video),
                        optionItem -> {
                            PlaybackPresenter.instance(getContext()).openVideo(mVideo);
                            mDialogPresenter.closeDialog();
                        }
                ));
    }

    private void appendPlayVideoIncognitoButton() {
        if (!mIsPlayVideoIncognitoButtonEnabled || mVideo == null || mVideo.videoId == null) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.play_video_incognito),
                        optionItem -> {
                            mVideo.incognito = true;
                            PlaybackPresenter.instance(getContext()).openVideo(mVideo);
                            mDialogPresenter.closeDialog();
                        }
                ));
    }

    private void showLongTextDialog(String description) {
        mDialogPresenter.appendLongTextCategory(mVideo.getTitle(), UiOptionItem.from(description));
        mDialogPresenter.showDialog(mVideo.getTitle());
    }

    private void appendSubscribeButton() {
        if (!mIsSubscribeButtonEnabled) {
            return;
        }

        if (mVideo == null || mVideo.isPlaylistAsChannel() || (!mVideo.isChannel() && !mVideo.hasVideo())) {
            return;
        }

        mVideo.isSubscribed = mVideo.isSubscribed || mVideo.belongsToSubscriptions() || mVideo.belongsToChannelUploads();

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(
                        mVideo.isSynced || mVideo.isSubscribed || (!mServiceManager.isSigned() && mVideo.channelId != null) ? mVideo.isSubscribed ?
                                R.string.unsubscribe_from_channel : R.string.subscribe_to_channel : R.string.subscribe_unsubscribe_from_channel),
                        optionItem -> toggleSubscribe()));
    }

    private void appendReturnToBackgroundVideoButton() {
        if (!mIsReturnToBackgroundVideoEnabled || !PlaybackPresenter.instance(getContext()).isRunningInBackground()) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.return_to_background_video),
                        // Assume that the Playback view already blocked and remembered.
                        optionItem -> getViewManager().startView(PlaybackView.class)
                )
        );
    }

    private void appendAddToPlaybackQueueButton() {
        if (!mIsAddToPlaybackQueueButtonEnabled) {
            return;
        }

        if (mVideo == null || !mVideo.hasVideo()) {
            return;
        }

        Playlist playlist = Playlist.instance();
        // Toggle between add/remove while dialog is opened
        boolean containsVideo = playlist.containsAfterCurrent(mVideo);

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(
                        getContext().getString(containsVideo ? R.string.remove_from_playback_queue : R.string.add_to_playback_queue),
                        optionItem -> {
                            if (containsVideo) {
                                playlist.remove(mVideo);
                                if (mCallback != null) {
                                    mCallback.onItemAction(mVideo, VideoMenuCallback.ACTION_REMOVE_FROM_QUEUE);
                                }
                            } else {
                                mVideo.fromQueue = true;
                                playlist.add(mVideo);
                                if (mCallback != null) {
                                    mCallback.onItemAction(mVideo, VideoMenuCallback.ACTION_ADD_TO_QUEUE);
                                }
                            }

                            closeDialog();
                            MessageHelpers.showMessage(getContext(), String.format("%s: %s",
                                    mVideo.getAuthor(),
                                    getContext().getString(containsVideo ? R.string.removed_from_playback_queue : R.string.added_to_playback_queue))
                            );
                        }));
    }

    private void appendPlayNextButton() {
        if (!mIsPlayNextButtonEnabled) {
            return;
        }

        if (mVideo == null || !mVideo.hasVideo()) {
            return;
        }

        Playlist playlist = Playlist.instance();

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(
                        getContext().getString(R.string.play_next),
                        optionItem -> {
                            mVideo.fromQueue = true;
                            playlist.next(mVideo);
                            if (mCallback != null) {
                                mCallback.onItemAction(mVideo, VideoMenuCallback.ACTION_PLAY_NEXT);
                            }

                            closeDialog();
                            MessageHelpers.showMessage(getContext(), String.format("%s: %s",
                                    mVideo.getAuthor(),
                                    getContext().getString(R.string.play_next))
                            );
                        }));
    }

    private void appendShowPlaybackQueueButton() {
        if (!mIsShowPlaybackQueueButtonEnabled) {
            return;
        }

        if (mVideo == null || !mVideo.hasVideo()) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(
                        getContext().getString(R.string.action_playback_queue),
                        optionItem -> AppDialogUtil.showPlaybackQueueDialog(getContext(), video -> PlaybackPresenter.instance(getContext()).openVideo(video))
                )
        );
    }

    private void appendPlaylistOrderButton() {
        if (!mIsPlaylistOrderButtonEnabled) {
            return;
        }

        BrowsePresenter presenter = BrowsePresenter.instance(getContext());

        if (mVideo == null || !(presenter.isPlaylistsSection() && presenter.inForeground())) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(
                        R.string.playlist_order),
                        optionItem -> AppDialogUtil.showPlaylistOrderDialog(getContext(), mVideo, mDialogPresenter::closeDialog)
                ));
    }

    private void appendStreamReminderButton() {
        if (!mIsStreamReminderButtonEnabled) {
            return;
        }

        if (mVideo == null || !mVideo.isUpcoming) {
            return;
        }

        StreamReminderService reminderService = StreamReminderService.instance(getContext());
        boolean reminderSet = reminderService.isReminderSet(mVideo);

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(reminderSet ? R.string.unset_stream_reminder : R.string.set_stream_reminder),
                        optionItem -> {
                            reminderService.toggleReminder(mVideo);
                            closeDialog();
                            MessageHelpers.showMessage(getContext(), reminderSet ? R.string.msg_done : R.string.playback_starts_shortly);
                        }
                ));
    }

    private void addRemoveFromPlaylist(String playlistId, String playlistTitle, boolean add) {
        RxHelper.disposeActions(mAddToPlaylistAction);
        if (add) {
            Observable<Void> editObserve = mVideo.mediaItem != null ?
                    mMediaItemService.addToPlaylistObserve(playlistId, mVideo.mediaItem) : mMediaItemService.addToPlaylistObserve(playlistId, mVideo.videoId);
            // Handle error: Maximum playlist size exceeded (> 5000 items)
            mAddToPlaylistAction = RxHelper.execute(editObserve, error -> MessageHelpers.showLongMessage(getContext(), error.getMessage()));
            mDialogPresenter.closeDialog();
            MessageHelpers.showMessage(getContext(),
                    getContext().getString(R.string.added_to, playlistTitle));
        } else {
            // Check that the current video belongs to the right section
            if (mCallback != null && Helpers.equals(mVideo.playlistId, playlistId)) {
                mCallback.onItemAction(mVideo, VideoMenuCallback.ACTION_REMOVE_FROM_PLAYLIST);
            }
            Observable<Void> editObserve = mMediaItemService.removeFromPlaylistObserve(playlistId, mVideo.videoId);
            mAddToPlaylistAction = RxHelper.execute(editObserve);
            mDialogPresenter.closeDialog();
            MessageHelpers.showMessage(getContext(),
                    getContext().getString(R.string.removed_from, playlistTitle));
        }
    }

    //private void addRemoveFromPlaylist(String playlistId, String playlistTitle, boolean add) {
    //    if (add) {
    //        Observable<Void> editObserve = mItemManager.addToPlaylistObserve(playlistId, mVideo.videoId);
    //        mAddToPlaylistAction = RxUtils.execute(editObserve);
    //        mDialogPresenter.closeDialog();
    //        MessageHelpers.showMessage(getContext(),
    //                getContext().getString(R.string.added_to, playlistTitle));
    //    } else {
    //        AppDialogUtil.showConfirmationDialog(getContext(), () -> {
    //            // Check that the current video belongs to the right section
    //            if (mCallback != null && Helpers.equals(mVideo.playlistId, playlistId)) {
    //                mCallback.onItemAction(mVideo, VideoMenuCallback.ACTION_REMOVE_FROM_PLAYLIST);
    //            }
    //            Observable<Void> editObserve = mItemManager.removeFromPlaylistObserve(playlistId, mVideo.videoId);
    //            mAddToPlaylistAction = RxUtils.execute(editObserve);
    //            mDialogPresenter.closeDialog();
    //            MessageHelpers.showMessage(getContext(),
    //                    getContext().getString(R.string.removed_from, playlistTitle));
    //        }, getContext().getString(R.string.dialog_remove_from, playlistTitle));
    //    }
    //}

    private void toggleSubscribe() {
        if (mVideo == null) {
            return;
        }

        //mVideo.isSynced = true; // default to subscribe

        // Until synced we won't really know weather we subscribed to a channel.
        // Exclusion: channel item (can't be synced)
        // Note, regular items (from subscribed section etc) aren't contain channel id
        if (mVideo.isSynced || mVideo.isChannel() || (!mServiceManager.isSigned() && mVideo.channelId != null)) {
            toggleSubscribe(mVideo);
        } else {
            MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);

            mServiceManager.loadMetadata(mVideo, metadata -> {
                mVideo.sync(metadata);
                toggleSubscribe(mVideo);
            });
        }
    }

    private void toggleSubscribe(Video video) {
        if (video == null) {
            return;
        }

        RxHelper.disposeActions(mSubscribeAction);

        Observable<Void> observable = video.isSubscribed ?
                mMediaItemService.unsubscribeObserve(video.channelId) : mMediaItemService.subscribeObserve(video.channelId);

        mSubscribeAction = RxHelper.execute(observable);

        video.isSubscribed = !video.isSubscribed;

        if (!video.isSubscribed && mCallback != null) {
            mCallback.onItemAction(video, VideoMenuCallback.ACTION_UNSUBSCRIBE);
        }

        MessageHelpers.showMessage(getContext(), getContext().getString(!video.isSubscribed ? R.string.unsubscribed_from_channel : R.string.subscribed_to_channel));
    }

    @Override
    protected void updateEnabledMenuItems() {
        super.updateEnabledMenuItems();

        MainUIData mainUIData = MainUIData.instance(getContext());

        mIsOpenChannelUploadsButtonEnabled = true;
        mIsOpenPlaylistButtonEnabled = true;
        mIsReturnToBackgroundVideoEnabled = true;
        mIsOpenChannelButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_OPEN_CHANNEL);
        mIsAddToRecentPlaylistButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_RECENT_PLAYLIST);
        mIsAddToPlaybackQueueButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_ADD_TO_QUEUE);
        mIsPlayNextButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_PLAY_NEXT);
        mIsAddToPlaylistButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_ADD_TO_PLAYLIST);
        mIsShareLinkButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_SHARE_LINK);
        mIsShareQRLinkButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_SHARE_QR_LINK);
        mIsShareEmbedLinkButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_SHARE_EMBED_LINK);
        mIsNotInterestedButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_NOT_INTERESTED);
        mIsNotRecommendChannelEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_NOT_RECOMMEND_CHANNEL);
        mIsRemoveFromHistoryButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_REMOVE_FROM_HISTORY);
        mIsRemoveFromSubscriptionsButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_REMOVE_FROM_SUBSCRIPTIONS);
        mIsOpenDescriptionButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_OPEN_DESCRIPTION);
        mIsPlayVideoButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_PLAY_VIDEO);
        mIsPlayVideoIncognitoButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_PLAY_VIDEO_INCOGNITO);
        mIsSubscribeButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_SUBSCRIBE);
        mIsStreamReminderButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_STREAM_REMINDER);
        mIsShowPlaybackQueueButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_SHOW_QUEUE);
        mIsPlaylistOrderButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_PLAYLIST_ORDER);
        mIsMarkAsWatchedButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_MARK_AS_WATCHED);
        mIsOpenCommentsButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_OPEN_COMMENTS);
    }

    private void initMenuMapping() {
        mMenuMapping.clear();

        mMenuMapping.put(MainUIData.MENU_ITEM_PLAY_VIDEO, new MenuAction(this::appendPlayVideoButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_PLAY_VIDEO_INCOGNITO, new MenuAction(this::appendPlayVideoIncognitoButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_REMOVE_FROM_HISTORY, new MenuAction(this::appendRemoveFromHistoryButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_STREAM_REMINDER, new MenuAction(this::appendStreamReminderButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_RECENT_PLAYLIST, new MenuAction(this::appendAddToRecentPlaylistButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_ADD_TO_PLAYLIST, new MenuAction(this::appendAddToPlaylistButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_CREATE_PLAYLIST, new MenuAction(this::appendCreatePlaylistButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_RENAME_PLAYLIST, new MenuAction(this::appendRenamePlaylistButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_ADD_TO_NEW_PLAYLIST, new MenuAction(this::appendAddToNewPlaylistButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_NOT_INTERESTED, new MenuAction(this::appendNotInterestedButton, true));
        mMenuMapping.put(MainUIData.MENU_ITEM_NOT_RECOMMEND_CHANNEL, new MenuAction(this::appendNotRecommendChannelButton, true));
        mMenuMapping.put(MainUIData.MENU_ITEM_REMOVE_FROM_SUBSCRIPTIONS, new MenuAction(() -> { appendRemoveFromSubscriptionsButton(); appendRemoveFromNotificationsButton(); }, true));
        mMenuMapping.put(MainUIData.MENU_ITEM_MARK_AS_WATCHED, new MenuAction(this::appendMarkAsWatchedButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_PLAYLIST_ORDER, new MenuAction(this::appendPlaylistOrderButton, true));
        mMenuMapping.put(MainUIData.MENU_ITEM_ADD_TO_QUEUE, new MenuAction(this::appendAddToPlaybackQueueButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_PLAY_NEXT, new MenuAction(this::appendPlayNextButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_SHOW_QUEUE, new MenuAction(this::appendShowPlaybackQueueButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_OPEN_CHANNEL, new MenuAction(this::appendOpenChannelButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_OPEN_PLAYLIST, new MenuAction(this::appendOpenPlaylistButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_SUBSCRIBE, new MenuAction(this::appendSubscribeButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_EXCLUDE_FROM_CONTENT_BLOCK, new MenuAction(this::appendToggleExcludeFromContentBlockButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_PIN_TO_SIDEBAR, new MenuAction(this::appendTogglePinVideoToSidebarButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_SAVE_REMOVE_PLAYLIST, new MenuAction(this::appendSaveRemovePlaylistButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_OPEN_DESCRIPTION, new MenuAction(this::appendOpenDescriptionButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_SHARE_LINK, new MenuAction(this::appendShareLinkButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_SHARE_QR_LINK, new MenuAction(this::appendShareQRLinkButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_SHARE_EMBED_LINK, new MenuAction(this::appendShareEmbedLinkButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_SELECT_ACCOUNT, new MenuAction(this::appendAccountSelectionButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_TOGGLE_HISTORY, new MenuAction(this::appendToggleHistoryButton, true));
        mMenuMapping.put(MainUIData.MENU_ITEM_CLEAR_HISTORY, new MenuAction(this::appendClearHistoryButton, true));
        mMenuMapping.put(MainUIData.MENU_ITEM_OPEN_COMMENTS, new MenuAction(this::appendOpenCommentsButton, false));

        for (ContextMenuProvider provider : new ContextMenuManager(getContext()).getProviders()) {
            if (provider.getMenuType() != ContextMenuProvider.MENU_TYPE_VIDEO) {
                continue;
            }
            mMenuMapping.put(provider.getId(), new MenuAction(() -> appendContextMenuItem(provider), false));
        }
    }

    private void appendContextMenuItem(ContextMenuProvider provider) {
        MainUIData mainUIData = MainUIData.instance(getContext());
        if (mainUIData.isMenuItemEnabled(provider.getId()) && provider.isEnabled(getVideo())) {
            mDialogPresenter.appendSingleButton(
                    UiOptionItem.from(getContext().getString(provider.getTitleResId()), optionItem -> provider.onClicked(getVideo(), getCallback()))
            );
        }
    }
}
