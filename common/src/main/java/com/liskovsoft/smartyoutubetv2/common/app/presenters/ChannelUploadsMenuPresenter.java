package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.utils.RxUtils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class ChannelUploadsMenuPresenter extends BasePresenter<Void> {
    private final MediaItemManager mItemManager;
    private final AppSettingsPresenter mSettingsPresenter;
    private Disposable mUnsubscribeAction;
    private Video mVideo;

    private ChannelUploadsMenuPresenter(Context context) {
        super(context);
        MediaService service = YouTubeMediaService.instance();
        mItemManager = service.getMediaItemManager();
        mSettingsPresenter = AppSettingsPresenter.instance(context);
    }

    public static ChannelUploadsMenuPresenter instance(Context context) {
        return new ChannelUploadsMenuPresenter(context);
    }

    public void showMenu(Video video) {
        if (video == null || !video.isChannelUploads()) {
            return;
        }

        mVideo = video;

        prepareAndShowDialog();
    }

    private void prepareAndShowDialog() {
        mSettingsPresenter.clear();
        
        appendOpenChannelUploadsButton();
        appendUnsubscribeButton();

        mSettingsPresenter.showDialog(mVideo.title, () -> RxUtils.disposeActions(mUnsubscribeAction));
    }

    private void appendOpenChannelUploadsButton() {
        if (mVideo == null) {
            return;
        }

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.open_channel_uploads), optionItem -> ChannelUploadsPresenter.instance(getContext()).openChannel(mVideo)));
    }

    private void appendUnsubscribeButton() {
        if (mVideo == null) {
            return;
        }

        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.unsubscribe), optionItem -> {
                    // Maybe this is subscribed items view
                    ChannelUploadsPresenter.instance(getContext())
                            .obtainVideoGroup(mVideo, group -> unsubscribe(group.getChannelId()));
                }));
    }

    private void unsubscribe(String channelId) {
        mUnsubscribeAction = mItemManager.unsubscribeObserve(channelId)
                .subscribeOn(Schedulers.newThread())
                .subscribe();

        MessageHelpers.showMessage(getContext(), R.string.unsubscribed_from_channel);
    }
}
