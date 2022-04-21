package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu;

import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.VideoPlaylistInfo;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelUploadsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class VideoMenuPresenter extends BaseMenuPresenter {
    private static final String TAG = VideoMenuPresenter.class.getSimpleName();
    private final MediaItemManager mItemManager;
    private final AppDialogPresenter mDialogPresenter;
    private final MediaServiceManager mServiceManager;
    private Disposable mPlaylistInfoAction;
    private Disposable mAddToPlaylistAction;
    private Disposable mNotInterestedAction;
    private Disposable mSubscribeAction;
    private Video mVideo;
    public static WeakReference<Video> sVideoHolder = new WeakReference<>(null);
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
    private boolean mIsOpenDescriptionButtonEnabled;
    private boolean mIsPlayVideoButtonEnabled;
    private VideoMenuCallback mCallback;

    public interface VideoMenuCallback {
        int ACTION_UNDEFINED = 0;
        int ACTION_UNSUBSCRIBE = 1;
        int ACTION_REMOVE = 2;
        int ACTION_PLAYLIST_REMOVE = 3;
        void onItemAction(Video videoItem, int action);
    }

    private VideoMenuPresenter(Context context) {
        super(context);
        MediaService service = YouTubeMediaService.instance();
        mItemManager = service.getMediaItemManager();
        mServiceManager = MediaServiceManager.instance();
        mDialogPresenter = AppDialogPresenter.instance(context);
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
    protected boolean isPinToSidebarEnabled() {
        return mIsPinToSidebarEnabled;
    }

    @Override
    protected boolean isAccountSelectionEnabled() {
        return mIsAccountSelectionEnabled;
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
        mIsOpenDescriptionButtonEnabled = true;
        mIsPlayVideoButtonEnabled = true;

        showMenuInt(video);
    }

    private void showMenuInt(Video video) {
        if (video == null) {
            return;
        }

        updateEnabledMenuItems();

        RxUtils.disposeActions(mPlaylistInfoAction, mAddToPlaylistAction, mNotInterestedAction, mSubscribeAction);

        mVideo = video;
        sVideoHolder = new WeakReference<>(video);

        MediaServiceManager.instance().authCheck(this::obtainPlaylistsAndShowDialogSigned, this::prepareAndShowDialogUnsigned);
    }

    private void obtainPlaylistsAndShowDialogSigned() {
        if (!mIsAddToPlaylistButtonEnabled || mVideo == null) {
            prepareAndShowDialogSigned(null);
            return;
        }

        mPlaylistInfoAction = mItemManager.getVideoPlaylistsInfosObserve(mVideo.videoId)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::prepareAndShowDialogSigned,
                        error -> {
                            prepareAndShowDialogSigned(null); // fallback to something on error
                            Log.e(TAG, "Get playlists error: %s", error.getMessage());
                        }
                );
    }

    private void prepareAndShowDialogSigned(List<VideoPlaylistInfo> videoPlaylistInfos) {
        if (getContext() == null) {
            return;
        }

        mDialogPresenter.clear();

        appendPlayVideoButton();
        appendReturnToBackgroundVideoButton();
        appendNotInterestedButton();
        appendAddToRecentPlaylistButton(videoPlaylistInfos);
        appendAddToPlaylistButton(videoPlaylistInfos);
        appendOpenChannelButton();
        //appendOpenChannelUploadsButton();
        appendSubscribeButton();
        appendOpenPlaylistButton();
        appendTogglePinVideoToSidebarButton();
        appendOpenDescriptionButton();
        appendAddToPlaybackQueueButton();
        appendShareButton();
        appendAccountSelectionButton();

        if (!mDialogPresenter.isEmpty()) {
            String title = mVideo != null ? mVideo.title : null;
            mDialogPresenter.showDialog(title, () -> RxUtils.disposeActions(mPlaylistInfoAction));
        }
    }

    private void prepareAndShowDialogUnsigned() {
        if (getContext() == null) {
            return;
        }

        mDialogPresenter.clear();

        appendPlayVideoButton();
        appendReturnToBackgroundVideoButton();
        appendOpenPlaylistButton();
        appendOpenChannelButton();
        appendTogglePinVideoToSidebarButton();
        appendOpenDescriptionButton();
        appendAddToPlaybackQueueButton();
        appendShareButton();
        appendAccountSelectionButton();

        if (mDialogPresenter.isEmpty()) {
            MessageHelpers.showMessage(getContext(), R.string.msg_signed_users_only);
        } else {
            mDialogPresenter.showDialog(mVideo.title, () -> RxUtils.disposeActions(mPlaylistInfoAction));
        }
    }

    private void appendAddToPlaylistButton(List<VideoPlaylistInfo> videoPlaylistInfos) {
        if (!mIsAddToPlaylistButtonEnabled || videoPlaylistInfos == null) {
            return;
        }

        if (mVideo == null || !mVideo.hasVideo() || mVideo.isChannelPlaylist()) {
            return;
        }

        List<OptionItem> options = new ArrayList<>();

        for (VideoPlaylistInfo playlistInfo : videoPlaylistInfos) {
            options.add(UiOptionItem.from(
                    playlistInfo.getTitle(),
                    (item) -> {
                        addRemoveFromPlaylist(playlistInfo.getPlaylistId(), item.isSelected());
                        GeneralData.instance(getContext()).setLastPlaylistId(playlistInfo.getPlaylistId());
                    },
                    playlistInfo.isSelected()));
        }

        mDialogPresenter.appendCheckedCategory(getContext().getString(R.string.dialog_add_to_playlist), options);
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
                mDialogPresenter.appendSingleButton(
                        UiOptionItem.from(getContext().getString(
                                playlistInfo.isSelected() ? R.string.dialog_remove_from : R.string.dialog_add_to, playlistInfo.getTitle()),
                                optionItem -> {
                                    addRemoveFromPlaylist(playlistInfo.getPlaylistId(), !playlistInfo.isSelected());
                                    MessageHelpers.showMessage(getContext(), getContext().getString(
                                            playlistInfo.isSelected() ? R.string.removed_from : R.string.added_to, playlistInfo.getTitle()
                                    ));
                                    mDialogPresenter.closeDialog();
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

        // Prepare to special type of channels that work as playlist
        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(
                        mVideo.isChannelPlaylist() ? R.string.open_playlist : R.string.open_channel), optionItem -> Utils.chooseChannelPresenter(getContext(), mVideo)));
    }

    private void appendOpenPlaylistButton() {
        if (!mIsOpenPlaylistButtonEnabled) {
            return;
        }

        if (mVideo == null || !mVideo.hasPlaylist()) {
            return;
        }

        // Prepare to special type of channels that work as playlist
        if (mVideo.isChannelPlaylist() && ChannelPresenter.canOpenChannel(mVideo)) {
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

        if (mVideo.belongsToHistory() && !mIsRemoveFromHistoryButtonEnabled) {
            return;
        }

        if (!mVideo.belongsToHistory() && !mIsNotInterestedButtonEnabled) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(mVideo.belongsToHistory() ? R.string.remove_from_history : R.string.not_interested), optionItem -> {
                    mNotInterestedAction = mItemManager.markAsNotInterestedObserve(mVideo.mediaItem)
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                    var -> {},
                                    error -> Log.e(TAG, "Mark as 'not interested' error: %s", error.getMessage()),
                                    () -> {
                                        if (mCallback != null) {
                                            mCallback.onItemAction(mVideo, VideoMenuCallback.ACTION_REMOVE);
                                        } else {
                                            MessageHelpers.showMessage(getContext(), mVideo.belongsToHistory() ? R.string.removed_from_history : R.string.you_wont_see_this_video);
                                        }
                                    }
                            );
                    mDialogPresenter.closeDialog();
                }));
    }

    private void appendShareButton() {
        if (!mIsShareButtonEnabled) {
            return;
        }

        AppDialogUtil.appendShareDialogItems(getContext(), mDialogPresenter, mVideo);
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

    private void appendPlayVideoButton() {
        if (!mIsPlayVideoButtonEnabled || mVideo == null) {
            return;
        }

        if (mVideo.videoId == null) {
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

    private void showLongTextDialog(String description) {
        mDialogPresenter.clear();
        mDialogPresenter.appendLongTextCategory(mVideo.title, UiOptionItem.from(description, null));
        mDialogPresenter.showDialog(mVideo.title);
    }

    private void appendSubscribeButton() {
        if (!mIsSubscribeButtonEnabled) {
            return;
        }

        if (mVideo == null || mVideo.isChannelPlaylist() || (!mVideo.canSubscribe() && !mVideo.hasVideo())) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(
                        // Note: Undefined section usually is subscribed channels content
                        mVideo.isSubscribed || mVideo.belongsToSubscriptions() || mVideo.belongsToChannelUploads() || mVideo.belongsToUndefined() ?
                                R.string.unsubscribe_from_channel : R.string.subscribe_unsubscribe_from_channel),
                        optionItem -> toggleSubscribe()));
    }

    private void appendReturnToBackgroundVideoButton() {
        if (!mIsReturnToBackgroundVideoEnabled || !PlaybackPresenter.instance(getContext()).isRunningInBackground()) {
            return;
        }

        mDialogPresenter.appendSingleButton(
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

        mDialogPresenter.appendSingleButton(
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

    private void addRemoveFromPlaylist(String playlistId, boolean checked) {
        RxUtils.disposeActions(mPlaylistInfoAction, mAddToPlaylistAction);
        Observable<Void> editObserve;

        if (checked) {
            editObserve = mItemManager.addToPlaylistObserve(playlistId, mVideo.videoId);
        } else {
            // Check that the current video belongs to the right section
            if (mCallback != null && Helpers.equals(mVideo.playlistId, playlistId)) {
                mCallback.onItemAction(mVideo, VideoMenuCallback.ACTION_PLAYLIST_REMOVE);
            }
            editObserve = mItemManager.removeFromPlaylistObserve(playlistId, mVideo.videoId);
        }

        mAddToPlaylistAction = RxUtils.execute(editObserve);
    }

    private void toggleSubscribe() {
        if (mVideo == null) {
            return;
        }

        // Until synced we won't really know weather we subscribed to a channel.
        // Exclusion: channel item (can't be synced)
        // Note, regular items (from subscribed section etc) aren't contain channel id
        if (mVideo.isSynced || mVideo.canSubscribe()) {
            toggleSubscribe(mVideo);
        } else {
            MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);

            mServiceManager.loadMetadata(mVideo, metadata -> {
                Video video = Video.from(mVideo);
                video.sync(metadata);
                toggleSubscribe(video);
            });
        }
    }

    private void toggleSubscribe(Video video) {
        if (video == null) {
            return;
        }

        Observable<Void> observable = video.isSubscribed ?
                mItemManager.unsubscribeObserve(video.channelId) : mItemManager.subscribeObserve(video.channelId);

        mSubscribeAction = RxUtils.execute(observable);

        video.isSubscribed = !video.isSubscribed;

        if (!video.isSubscribed && mCallback != null) {
            mCallback.onItemAction(video, VideoMenuCallback.ACTION_UNSUBSCRIBE);
        }

        MessageHelpers.showMessage(getContext(),
                video.extractAuthor() + ": " + getContext().getString(!video.isSubscribed ? R.string.unsubscribed_from_channel : R.string.subscribed_to_channel));
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

        if (!mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_OPEN_DESCRIPTION)) {
            mIsOpenDescriptionButtonEnabled = false;
        }
        
        if (!mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_PLAY_VIDEO)) {
            mIsPlayVideoButtonEnabled = false;
        }
    }
}
