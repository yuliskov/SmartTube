package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs;

import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
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
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
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
    private Video mSection;
    private boolean mIsNotInterestedButtonEnabled;
    private boolean mIsOpenChannelButtonEnabled;
    private boolean mIsOpenChannelUploadsButtonEnabled;
    private boolean mIsSubscribeButtonEnabled;
    private boolean mIsShareButtonEnabled;
    private boolean mIsAddToPlaylistButtonEnabled;
    private boolean mIsAccountSelectionEnabled;
    private boolean mIsReturnToBackgroundVideoEnabled;
    private boolean mIsPinToSidebarEnabled;
    private boolean mIsOpenPlaylistButtonEnabled;
    private boolean mIsAddToPlaybackQueueButtonEnabled;

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

        showMenuInt(video);
    }

    public void showVideoMenu(Video video) {
        showVideoMenu(video, null);
    }

    public void showVideoMenu(Video video, Video section) {
        mIsAddToPlaylistButtonEnabled = true;
        mIsAddToPlaybackQueueButtonEnabled = true;
        mIsOpenChannelButtonEnabled = true;
        mIsOpenChannelUploadsButtonEnabled = true;
        mIsOpenPlaylistButtonEnabled = true;
        mIsSubscribeButtonEnabled = true;
        mIsNotInterestedButtonEnabled = true;
        mIsShareButtonEnabled = true;
        mIsAccountSelectionEnabled = true;
        mIsReturnToBackgroundVideoEnabled = true;
        mIsPinToSidebarEnabled = true;

        showMenuInt(video, section);
    }

    public void showChannelMenu(Video video) {
        mIsSubscribeButtonEnabled = true;
        mIsShareButtonEnabled = true;
        mIsOpenChannelButtonEnabled = true;
        mIsAccountSelectionEnabled = true;
        mIsReturnToBackgroundVideoEnabled = true;

        showMenuInt(video);
    }

    public void showPlaylistMenu(Video section) {
        mIsPinToSidebarEnabled = true;

        showMenuInt(null, section);
    }

    private void showMenuInt(Video video) {
        showMenuInt(video, null);
    }

    private void showMenuInt(Video video, Video section) {
        if (video == null && section == null) {
            return;
        }

        RxUtils.disposeActions(mPlaylistAction, mAddAction, mNotInterestedAction, mSubscribeAction);

        mVideo = video;
        mSection = section;

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
        appendAddToPlaylistButton(videoPlaylistInfos);
        appendAddToPlaybackQueueButton();
        appendPinToSidebarButton();
        appendOpenPlaylistButton();
        appendOpenChannelButton();
        //appendOpenChannelUploadsButton();
        appendSubscribeButton();
        appendNotInterestedButton();
        appendShareButton();
        appendAccountSelectionButton();

        if (!mSettingsPresenter.isEmpty()) {
            String title = mVideo != null ? mVideo.title : mSection.title;
            mSettingsPresenter.showDialog(title, () -> RxUtils.disposeActions(mPlaylistAction));
        }
    }

    private void prepareAndShowDialogUnsigned() {
        if (getContext() == null) {
            return;
        }

        mSettingsPresenter.clear();

        appendReturnToBackgroundVideoButton();
        appendAddToPlaybackQueueButton();
        appendPinToSidebarButton();
        appendOpenPlaylistButton();
        appendOpenChannelButton();
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

        List<OptionItem> options = new ArrayList<>();

        for (VideoPlaylistInfo playlistInfo : videoPlaylistInfos) {
            options.add(UiOptionItem.from(
                    playlistInfo.getTitle(),
                    (item) -> addToPlaylist(playlistInfo.getPlaylistId(), item.isSelected()),
                    playlistInfo.isSelected()));
        }

        mSettingsPresenter.appendCheckedCategory(getContext().getString(R.string.dialog_add_to_playlist), options);
    }

    private void appendOpenChannelButton() {
        if (!mIsOpenChannelButtonEnabled) {
            return;
        }

        if (!ChannelPresenter.canOpenChannel(mVideo)) {
            return;
        }

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.open_channel), optionItem -> ChannelPresenter.instance(getContext()).openChannel(mVideo)));
    }

    private void appendOpenPlaylistButton() {
        if (!mIsOpenPlaylistButtonEnabled) {
            return;
        }

        if (mVideo == null || !mVideo.isPlaylist()) {
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
        if (!mIsNotInterestedButtonEnabled || mVideo == null || mVideo.mediaItem == null || mVideo.mediaItem.getFeedbackToken() == null) {
            return;
        }

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.not_interested), optionItem -> {
                    mNotInterestedAction = mItemManager.markAsNotInterestedObserve(mVideo.mediaItem)
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                    var -> {},
                                    error -> Log.e(TAG, "Mark as 'not interested' error: %s", error.getMessage()),
                                    () -> MessageHelpers.showMessage(getContext(), R.string.you_wont_see_this_video)
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
        if (!mIsSubscribeButtonEnabled || mVideo == null) {
            return;
        }

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(
                        mVideo.isSubscribed ? R.string.unsubscribe_from_channel : R.string.subscribe_to_channel),
                        optionItem -> subscribe()));
    }

    private void appendPinToSidebarButton() {
        if (!mIsPinToSidebarEnabled) {
            return;
        }

        if ((mVideo == null || mVideo.playlistId == null) && mSection == null) {
            return;
        }

        // Pin non-user playlist
        if (mSection == null) {
            Video video = new Video();
            video.playlistId = mVideo.playlistId;
            video.title = String.format("%s - %s",
                    mVideo.author != null ? mVideo.author : mVideo.title,
                    mVideo.group != null && mVideo.group.getTitle() != null ? mVideo.group.getTitle() : mVideo.description
            );
            video.cardImageUrl = mVideo.cardImageUrl;
            mSection = video;
        }

        BrowsePresenter presenter = BrowsePresenter.instance(getContext());

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.pin_unpin_from_sidebar),
                        optionItem -> {
                            // Toggle between pin/unpin while dialog is opened
                            boolean isItemPinned = presenter.isItemPinned(mSection);

                            if (isItemPinned) {
                                presenter.unpinItem(mSection);
                            } else {
                                presenter.pinItem(mSection);
                            }
                            MessageHelpers.showMessage(getContext(), isItemPinned ? R.string.unpin_from_sidebar : R.string.pin_to_sidebar);
                        }));
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
        if (!mIsAddToPlaybackQueueButtonEnabled || mVideo == null) {
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

    private void subscribe() {
        if (mVideo == null) {
            return;
        }

        if (mVideo.channelId != null) {
            subscribeInt();
        } else {
            mServiceManager.loadMetadata(mVideo, metadata -> {
                 mVideo.channelId = metadata.getChannelId();
                 subscribeInt();
            });
        }
        
        MessageHelpers.showMessage(getContext(), mVideo.isSubscribed ? R.string.unsubscribed_from_channel : R.string.subscribed_to_channel);
    }

    private void subscribeInt() {
        if (mVideo == null) {
            return;
        }

        Observable<Void> observable = mVideo.isSubscribed ?
                mItemManager.unsubscribeObserve(mVideo.channelId) : mItemManager.subscribeObserve(mVideo.channelId);

        mSubscribeAction = RxUtils.execute(observable);
    }
}
