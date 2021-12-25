package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu;

import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelUploadsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter.VideoMenuCallback;
import com.liskovsoft.sharedutils.rx.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.disposables.Disposable;

public class ChannelUploadsMenuPresenter extends BasePresenter<Void> {
    private final MediaItemManager mItemManager;
    private final AppDialogPresenter mSettingsPresenter;
    private final MediaServiceManager mServiceManager;
    private Disposable mUnsubscribeAction;
    private Video mVideo;
    private VideoMenuCallback mCallback;

    private ChannelUploadsMenuPresenter(Context context) {
        super(context);
        MediaService service = YouTubeMediaService.instance();
        mItemManager = service.getMediaItemManager();
        mSettingsPresenter = AppDialogPresenter.instance(context);
        mServiceManager = MediaServiceManager.instance();
    }

    public static ChannelUploadsMenuPresenter instance(Context context) {
        return new ChannelUploadsMenuPresenter(context);
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
        mSettingsPresenter.clear();

        // Doesn't need this since this is the main action.
        //appendOpenChannelUploadsButton();
        appendOpenChannelButton();
        appendUnsubscribeButton();
        appendMarkAsWatched();

        mSettingsPresenter.showDialog(mVideo.title);
    }

    private void appendOpenChannelUploadsButton() {
        if (mVideo == null) {
            return;
        }

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.open_channel_uploads), optionItem -> ChannelUploadsPresenter.instance(getContext()).openChannel(mVideo)));
    }

    private void appendOpenChannelButton() {
        if (!ChannelPresenter.canOpenChannel(mVideo)) {
            return;
        }

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.open_channel), optionItem -> ChannelPresenter.instance(getContext()).openChannel(mVideo)));
    }

    private void appendUnsubscribeButton() {
        if (mVideo == null) {
            return;
        }

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.unsubscribe_from_channel), optionItem -> {
                    // Maybe this is subscribed items view
                    ChannelUploadsPresenter.instance(getContext())
                            .obtainVideoGroup(mVideo, group -> unsubscribe(group.getChannelId()));
                }));
    }

    private void appendMarkAsWatched() {
        if (mVideo == null || !mVideo.hasNewContent) {
            return;
        }

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.mark_channel_as_watched), optionItem -> {
                    mServiceManager.loadChannelUploads(mVideo, (group) -> {});
                    MessageHelpers.showMessage(getContext(), R.string.channel_marked_as_watched);
                }));
    }

    private void unsubscribe(String channelId) {
        RxUtils.disposeActions(mUnsubscribeAction);
        mUnsubscribeAction = RxUtils.execute(mItemManager.unsubscribeObserve(channelId));

        if (mCallback != null) {
            mSettingsPresenter.closeDialog();
            mCallback.onItemAction(mVideo, VideoMenuCallback.ACTION_UNSUBSCRIBE);
        } else {
            MessageHelpers.showMessage(getContext(), R.string.unsubscribed_from_channel);
        }
    }
}
