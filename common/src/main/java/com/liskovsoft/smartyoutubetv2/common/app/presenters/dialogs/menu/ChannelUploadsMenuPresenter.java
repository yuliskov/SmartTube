package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu;

import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.rx.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelUploadsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter.VideoMenuCallback;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.disposables.Disposable;

import java.util.List;

public class ChannelUploadsMenuPresenter extends BaseMenuPresenter {
    private final MediaItemManager mItemManager;
    private final AppDialogPresenter mDialogPresenter;
    private final MediaServiceManager mServiceManager;
    private Disposable mUnsubscribeAction;
    private Video mVideo;
    private VideoMenuCallback mCallback;

    private ChannelUploadsMenuPresenter(Context context) {
        super(context);
        MediaService service = YouTubeMediaService.instance();
        mItemManager = service.getMediaItemManager();
        mDialogPresenter = AppDialogPresenter.instance(context);
        mServiceManager = MediaServiceManager.instance();
    }

    public static ChannelUploadsMenuPresenter instance(Context context) {
        return new ChannelUploadsMenuPresenter(context);
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

    @Override
    protected boolean isPinToSidebarEnabled() {
        return true;
    }

    @Override
    protected boolean isSavePlaylistEnabled() {
        return true;
    }

    @Override
    protected boolean isCreatePlaylistEnabled() {
        return false;
    }

    @Override
    protected boolean isAddToNewPlaylistEnabled() {
        return false;
    }

    @Override
    protected boolean isAccountSelectionEnabled() {
        return true;
    }

    public void showMenu(Video video, VideoMenuCallback callback) {
        mCallback = callback;
        showMenu(video);
    }

    public void showMenu(Video video) {
        if (video == null || !video.belongsToChannelUploads()) {
            return;
        }

        mVideo = video;

        RxUtils.disposeActions(mUnsubscribeAction);

        prepareAndShowDialog();
    }

    private void prepareAndShowDialog() {
        mDialogPresenter.clear();

        // Doesn't need this since this is the main action.
        //appendOpenChannelUploadsButton();
        appendOpenChannelButton();
        appendUnsubscribeButton();
        appendMarkAsWatched();
        appendTogglePinVideoToSidebarButton();

        mDialogPresenter.showDialog(mVideo.title);
    }

    private void appendOpenChannelUploadsButton() {
        if (mVideo == null) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.open_channel_uploads), optionItem -> ChannelUploadsPresenter.instance(getContext()).openChannel(mVideo)));
    }

    private void appendOpenChannelButton() {
        if (!ChannelPresenter.canOpenChannel(mVideo)) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.open_channel), optionItem -> ChannelPresenter.instance(getContext()).openChannel(mVideo)));
    }

    private void appendUnsubscribeButton() {
        if (mVideo == null) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.unsubscribe_from_channel), optionItem -> {
                    // Maybe this is subscribed items view
                    ChannelUploadsPresenter.instance(getContext())
                            .obtainVideoGroup(mVideo, group -> {
                                // Some uploads groups doesn't contain channel button.
                                // Use data from first item instead.
                                if (group.getChannelId() == null) {
                                    List<MediaItem> mediaItems = group.getMediaItems();

                                    if (mediaItems != null && mediaItems.size() > 0) {
                                        mServiceManager.loadMetadata(mediaItems.get(0), metadata -> {
                                            unsubscribe(metadata.getChannelId());
                                            mVideo.channelId = metadata.getChannelId();
                                        });
                                    }

                                    return;
                                }

                                unsubscribe(group.getChannelId());
                                mVideo.channelId = group.getChannelId();
                            });
                }));
    }

    private void appendMarkAsWatched() {
        if (mVideo == null || !mVideo.hasNewContent) {
            return;
        }

        boolean contentAlreadyLoaded = mVideo.groupPosition == 0;

        if (contentAlreadyLoaded) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.mark_channel_as_watched), optionItem -> {
                    mServiceManager.loadChannelUploads(mVideo, (group) -> {});
                    MessageHelpers.showMessage(getContext(), R.string.channel_marked_as_watched);
                }));
    }

    private void unsubscribe(String channelId) {
        if (channelId == null) {
            return;
        }

        RxUtils.disposeActions(mUnsubscribeAction);
        mUnsubscribeAction = RxUtils.execute(mItemManager.unsubscribeObserve(channelId));

        if (mCallback != null) {
            mDialogPresenter.closeDialog();
            mCallback.onItemAction(mVideo, VideoMenuCallback.ACTION_UNSUBSCRIBE);
        } else {
            MessageHelpers.showMessage(getContext(), R.string.unsubscribed_from_channel);
        }
    }
}
