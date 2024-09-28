package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.channelgroup;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.yt.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.yt.ServiceManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.BrowseProcessor;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class ChannelGroupProcessor implements BrowseProcessor {
    private static final String TAG = ChannelGroupProcessor.class.getSimpleName();
    private final Context mContext;
    private final OnItemReady mOnItemReady;
    private final MediaItemService mItemService;

    public ChannelGroupProcessor(Context context, OnItemReady onItemReady) {
        mContext = context;
        mOnItemReady = onItemReady;
        ServiceManager service = YouTubeServiceManager.instance();
        mItemService = service.getMediaItemService();
    }

    @Override
    public void process(VideoGroup videoGroup) {
        if (videoGroup == null || videoGroup.isEmpty()) {
            return;
        }

        BrowseSection currentSection = BrowsePresenter.instance(mContext).getCurrentSection();
        Object data = currentSection.getData();

        if (!(data instanceof Video) || ((Video) data).channelGroupId == -1) {
            return;
        }

        Disposable result = Observable.fromIterable(videoGroup.getVideos())
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe(video -> {
                    mItemService.getMetadataObserve(video.mediaItem)
                            .blockingSubscribe(metadata -> {
                                 video.sync(metadata);
                                 mOnItemReady.onItemReady(video);
                            });
                });
    }
}
