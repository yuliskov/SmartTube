package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu;

import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.VideoPlaylistInfo;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelUploadsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.List;

public class VideoMenuPresenter extends BasePresenter<Void> {
    private static final String TAG = VideoMenuPresenter.class.getSimpleName();
    private final MediaItemManager mItemManager;
    private final AppDialogPresenter mSettingsPresenter;
    private final MediaServiceManager mServiceManager;
    private Disposable mPlaylistAction;
    private Disposable mAddAction;
    private Disposable mNotInterestedAction;
    private Disposable mSubscribeAction;
    private Video mVideo;
    private boolean mIsNotInterestedButtonEnabled;
    private boolean mIsRemoveFromHistoryButtonEnabled;
    private boolean mIsOpenChannelButtonEnabled;
    private boolean mIsOpenChannelUploadsButtonEnabled;
    private boolean mIsSubscribeButtonEnabled;
    private boolean mIsShareButtonEnabled;
    private boolean mIsAddToPlaylistButtonEnabled;
    private boolean mIsAddToRecentPlaylistButtonEnabled;
    private boolean mIsAccountSelectionEnabled;
    private boolean mIsReturnToBackgroundVideoEnabled;
    private boolean mIsPinToSidebarEnabled;
    private boolean mIsOpenPlaylistButtonEnabled;
    private boolean mIsAddToPlaybackQueueButtonEnabled;
    private VideoMenuCallback mCallback;

    public interface VideoMenuCallback {
        void onItemRemoved(Video item);
    }

    private VideoMenuPresenter(Context context) {
        super(context);
        MediaService service = YouTubeMediaService.instance();
        mItemManager = service.getMediaItemManager();
        mServiceManager = MediaServiceManager.instance();
        mSettingsPresenter = AppDialogPresenter.instance(context);
    }

    public static VideoMenuPresenter instance(Context context) {
        return new VideoMenuPresenter(context);
    }

    public void showAddToPlaylistMenu(Video video) {
        mIsAddToPlaylistButtonEnabled = true;
        mIsAddToRecentPlaylistButtonEnabled = true;

        showMenuInt(video);
    }

    public void showMenu(Video item, VideoMenuCallback callback) {
        mCallback = callback;
        showVideoMenu(item);
    }

    public void showMenu(Video item) {
        showVideoMenu(item);
    }

    public void showVideoMenu(Video video) {
        mIsAddToPlaylistButtonEnabled = true;
        mIsAddToRecentPlaylistButtonEnabled = true;
        mIsAddToPlaybackQueueButtonEnabled = true;
        mIsOpenChannelButtonEnabled = true;
        mIsOpenChannelUploadsButtonEnabled = true;
        mIsOpenPlaylistButtonEnabled = true;
        mIsSubscribeButtonEnabled = true;
        mIsNotInterestedButtonEnabled = true;
        mIsRemoveFromHistoryButtonEnabled = true;
        mIsShareButtonEnabled = true;
        mIsAccountSelectionEnabled = true;
        mIsReturnToBackgroundVideoEnabled = true;
        mIsPinToSidebarEnabled = true;

        showMenuInt(video);
    }

    private void showMenuInt(Video video) {
        if (video == null) {
            return;
        }

        updateEnabledMenuItems();

        RxUtils.disposeActions(mPlaylistAction, mAddAction, mNotInterestedAction, mSubscribeAction);

        mVideo = video;

        MediaServiceManager.instance().authCheck(this::obtainPlaylistsAndShowDialogSigned, this::prepareAndShowDialogUnsigned);
    }

    private void obtainPlaylistsAndShowDialogSigned() {
        if (!mIsAddToPlaylistButtonEnabled || mVideo == null) {
            prepareAndShowDialogSigned(null);
            return;
        }

        mPlaylistAction = mItemManager.getVideoPlaylistsInfosObserve(mVideo.videoId)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::prepareAndShowDialogSigned,
                        error -> Log.e(TAG, "Get playlists error: %s", error.getMessage())
                );
    }

    private void prepareAndShowDialogSigned(List<VideoPlaylistInfo> videoPlaylistInfos) {
        if (getContext() == null) {
            return;
        }

        mSettingsPresenter.clear();

        appendReturnToBackgroundVideoButton();
        appendNotInterestedButton();
        appendAddToRecentPlaylistButton(videoPlaylistInfos);
        appendAddToPlaylistButton(videoPlaylistInfos);
        appendOpenChannelButton();
        //appendOpenChannelUploadsButton();
        appendSubscribeButton();
        appendOpenPlaylistButton();
        appendPinToSidebarButton();
        appendAddToPlaybackQueueButton();
        appendShareButton();
        appendAccountSelectionButton();

        if (!mSettingsPresenter.isEmpty()) {
            String title = mVideo != null ? mVideo.title : null;
            mSettingsPresenter.showDialog(title, () -> RxUtils.disposeActions(mPlaylistAction));
        }
    }

    private void prepareAndShowDialogUnsigned() {
        if (getContext() == null) {
            return;
        }

        mSettingsPresenter.clear();

        appendReturnToBackgroundVideoButton();
        appendOpenPlaylistButton();
        appendOpenChannelButton();
        appendPinToSidebarButton();
        appendAddToPlaybackQueueButton();
        appendShareButton();
        appendAccountSelectionButton();

        if (mSettingsPresenter.isEmpty()) {
            MessageHelpers.showMessage(getContext(), R.string.msg_signed_users_only);
        } else {
            mSettingsPresenter.showDialog(mVideo.title, () -> RxUtils.disposeActions(mPlaylistAction));
        }
    }

    private void appendAddToPlaylistButton(List<VideoPlaylistInfo> videoPlaylistInfos) {
        if (!mIsAddToPlaylistButtonEnabled || videoPlaylistInfos == null) {
            return;
        }

        if (mVideo == null || !mVideo.hasVideo()) {
            return;
        }

        List<OptionItem> options = new ArrayList<>();

        for (VideoPlaylistInfo playlistInfo : videoPlaylistInfos) {
            options.add(UiOptionItem.from(
                    playlistInfo.getTitle(),
                    (item) -> {
                        addToPlaylist(playlistInfo.getPlaylistId(), item.isSelected());
                        GeneralData.instance(getContext()).setLastPlaylistId(playlistInfo.getPlaylistId());
                    },
                    playlistInfo.isSelected()));
        }

        mSettingsPresenter.appendCheckedCategory(getContext().getString(R.string.dialog_add_to_playlist), options);
    }

    private void appendAddToRecentPlaylistButton(List<VideoPlaylistInfo> videoPlaylistInfos) {
        if (!mIsAddToPlaylistButtonEnabled || videoPlaylistInfos == null) {
            return;
        }

        if (!mIsAddToRecentPlaylistButtonEnabled) {
            return;
        }

        if (mVideo == null || !mVideo.hasVideo()) {
            return;
        }

        String playlistId = GeneralData.instance(getContext()).getLastPlaylistId();

        if (playlistId == null) {
            return;
        }

        for (VideoPlaylistInfo playlistInfo : videoPlaylistInfos) {
            if (playlistInfo.getPlaylistId().equals(playlistId)) {
                mSettingsPresenter.appendSingleButton(
                        UiOptionItem.from(getContext().getString(
                                playlistInfo.isSelected() ? R.string.dialog_remove_from : R.string.dialog_add_to, playlistInfo.getTitle()),
                                optionItem -> {
                                    addToPlaylist(playlistInfo.getPlaylistId(), !playlistInfo.isSelected());
                                    MessageHelpers.showMessage(getContext(), getContext().getString(
                                            playlistInfo.isSelected() ? R.string.removed_from : R.string.added_to, playlistInfo.getTitle()
                                    ));
                                    mSettingsPresenter.closeDialog();
                                }
                        )
                );

                break;
            }
        }
    }

    private void appendOpenChannelButton() {
        if (!mIsOpenChannelButtonEnabled) {
            return;
        }

        if (!ChannelPresenter.canOpenChannel(mVideo)) {
            return;
        }

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.open_channel), optionItem -> Utils.chooseChannelPresenter(getContext(), mVideo)));
    }

    private void appendOpenPlaylistButton() {
        if (!mIsOpenPlaylistButtonEnabled) {
            return;
        }

        if (mVideo == null || !mVideo.hasPlaylist()) {
            return;
        }

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.open_playlist), optionItem -> ChannelUploadsPresenter.instance(getContext()).openChannel(mVideo)));
    }

    private void appendOpenChannelUploadsButton() {
        if (!mIsOpenChannelUploadsButtonEnabled || mVideo == null) {
            return;
        }

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.open_channel_uploads), optionItem -> ChannelUploadsPresenter.instance(getContext()).openChannel(mVideo)));
    }

    private void appendNotInterestedButton() {
        if (mVideo == null || mVideo.mediaItem == null || mVideo.mediaItem.getFeedbackToken() == null) {
            return;
        }

        if (mVideo.belongsToHistory() && !mIsRemoveFromHistoryButtonEnabled) {
            return;
        }

        if (!mVideo.belongsToHistory() && !mIsNotInterestedButtonEnabled) {
            return;
        }

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(mVideo.belongsToHistory() ? R.string.remove_from_history : R.string.not_interested), optionItem -> {
                    mNotInterestedAction = mItemManager.markAsNotInterestedObserve(mVideo.mediaItem)
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                    var -> {},
                                    error -> Log.e(TAG, "Mark as 'not interested' error: %s", error.getMessage()),
                                    () -> {
                                        //MessageHelpers.showMessage(getContext(), mVideo.belongToHistory() ? R.string.removed_from_history : R.string.you_wont_see_this_video);
                                        if (mCallback != null) {
                                            mCallback.onItemRemoved(mVideo);
                                        }
                                    }
                            );
                    mSettingsPresenter.closeDialog();
                }));
    }

    private void appendShareButton() {
        if (!mIsShareButtonEnabled || mVideo == null) {
            return;
        }

        if (mVideo.videoId == null && mVideo.channelId == null) {
            return;
        }

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.share_link), optionItem -> {
                    if (mVideo.videoId != null) {
                        Utils.displayShareVideoDialog(getContext(), mVideo.videoId);
                    } else if (mVideo.channelId != null) {
                        Utils.displayShareChannelDialog(getContext(), mVideo.channelId);
                    }
                }));

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.share_embed_link), optionItem -> {
                    if (mVideo.videoId != null) {
                        Utils.displayShareEmbedVideoDialog(getContext(), mVideo.videoId);
                    }
                }));
    }

    private void appendAccountSelectionButton() {
        if (!mIsAccountSelectionEnabled) {
            return;
        }

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.dialog_account_list), optionItem -> {
                    AccountSelectionPresenter.instance(getContext()).show(true);
                }));
    }

    private void appendSubscribeButton() {
        if (!mIsSubscribeButtonEnabled) {
            return;
        }

        if (mVideo == null || (!mVideo.hasChannel() && !mVideo.hasVideo())) {
            return;
        }

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.subscribe_unsubscribe_from_channel),
                        optionItem -> toggleSubscribe()));
    }

    private void appendPinToSidebarButton() {
        if (!mIsPinToSidebarEnabled) {
            return;
        }

        if (mVideo == null || (!mVideo.hasPlaylist() && !mVideo.hasUploads())) {
            return;
        }

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.pin_unpin_from_sidebar),
                        optionItem -> {
                            if (mVideo.hasPlaylist()) {
                                togglePinToSidebar(createPinnedSection(mVideo));
                            } else {
                                mServiceManager.loadChannelUploads(mVideo, group -> {
                                    if (group.getMediaItems() != null) {
                                        MediaItem firstItem = group.getMediaItems().get(0);

                                        Video section = createPinnedSection(Video.from(firstItem));
                                        section.title = mVideo.title;
                                        togglePinToSidebar(section);
                                    }
                                });
                            }
                        }));
    }

    private void togglePinToSidebar(Video section) {
        BrowsePresenter presenter = BrowsePresenter.instance(getContext());

        // Toggle between pin/unpin while dialog is opened
        boolean isItemPinned = presenter.isItemPinned(section);

        if (isItemPinned) {
            presenter.unpinItem(section);
        } else {
            presenter.pinItem(section);
        }
        MessageHelpers.showMessage(getContext(), isItemPinned ? R.string.unpinned_from_sidebar : R.string.pinned_to_sidebar);
    }

    private Video createPinnedSection(Video video) {
        if (video == null || (!video.hasPlaylist() && !video.hasUploads())) {
            return null;
        }

        String groupTitle = video.group != null && video.group.getTitle() != null ? video.group.getTitle() : null;
        boolean isSectionItem = video.group != null && video.group.getSection() != null;

        Video section = new Video();
        section.playlistId = video.playlistId;
        section.title = String.format("%s - %s",
                video.title != null && isSectionItem ? video.title : groupTitle != null ? groupTitle : video.title,
                video.author != null ? video.author : video.description
        );
        section.cardImageUrl = video.cardImageUrl;

        return section;
    }

    private void appendReturnToBackgroundVideoButton() {
        if (!mIsReturnToBackgroundVideoEnabled || !PlaybackPresenter.instance(getContext()).isRunningInBackground()) {
            return;
        }

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.return_to_background_video),
                        // Assume that the Playback view already blocked and remembered.
                        optionItem -> ViewManager.instance(getContext()).startView(SplashView.class)
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

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(
                        R.string.add_remove_from_playback_queue),
                        optionItem -> {
                            // Toggle between add/remove while dialog is opened
                            boolean containsVideo = playlist.contains(mVideo);

                            if (containsVideo) {
                                playlist.remove(mVideo);
                            } else {
                                playlist.add(mVideo);
                            }

                            MessageHelpers.showMessage(getContext(), containsVideo ? R.string.removed_from_playback_queue : R.string.added_to_playback_queue);
                        }));
    }

    private void addToPlaylist(String playlistId, boolean checked) {
        RxUtils.disposeActions(mPlaylistAction, mAddAction);
        Observable<Void> editObserve;

        if (checked) {
            editObserve = mItemManager.addToPlaylistObserve(playlistId, mVideo.videoId);
        } else {
            editObserve = mItemManager.removeFromPlaylistObserve(playlistId, mVideo.videoId);
        }

        mAddAction = RxUtils.execute(editObserve);
    }

    private void toggleSubscribe() {
        if (mVideo == null) {
            return;
        }

        // Until synced we won't really know weather we subscribed to a channel.
        // Exclusion: channel item (can't be synced)
        // Note, regular items (from subscribed section etc) aren't contain channel id
        if (mVideo.isSynced || mVideo.isChannel()) {
            toggleSubscribeInt();
        } else {
            MessageHelpers.showLongMessage(getContext(), R.string.wait_data_loading);

            mServiceManager.loadMetadata(mVideo, metadata -> {
                mVideo.sync(metadata);
                toggleSubscribeInt();
            });
        }
    }

    private void toggleSubscribeInt() {
        if (mVideo == null) {
            return;
        }

        Observable<Void> observable = mVideo.isSubscribed ?
                mItemManager.unsubscribeObserve(mVideo.channelId) : mItemManager.subscribeObserve(mVideo.channelId);

        mSubscribeAction = RxUtils.execute(observable);

        MessageHelpers.showMessage(getContext(), mVideo.isSubscribed ? R.string.unsubscribed_from_channel : R.string.subscribed_to_channel);

        mVideo.isSubscribed = !mVideo.isSubscribed;
    }

    private void updateEnabledMenuItems() {
        MainUIData mainUIData = MainUIData.instance(getContext());

        if (!mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_RECENT_PLAYLIST)) {
            mIsAddToRecentPlaylistButtonEnabled = false;
        }

        if (!mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_ADD_TO_QUEUE)) {
            mIsAddToPlaybackQueueButtonEnabled = false;
        }

        if (!mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_SHARE_LINK)) {
            mIsShareButtonEnabled = false;
        }

        if (!mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_PIN_TO_SIDEBAR)) {
            mIsPinToSidebarEnabled = false;
        }

        if (!mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_SELECT_ACCOUNT)) {
            mIsAccountSelectionEnabled = false;
        }

        if (!mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_NOT_INTERESTED)) {
            mIsNotInterestedButtonEnabled = false;
        }

        if (!mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_REMOVE_FROM_HISTORY)) {
            mIsRemoveFromHistoryButtonEnabled = false;
        }
    }
}
