package com.liskovsoft.smartyoutubetv2.common.utils;

import com.liskovsoft.appupdatechecker2.other.SettingsManager;
import com.liskovsoft.mediaserviceinterfaces.MediaGroupManager;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class ServiceManager {
    private static final String TAG = SettingsManager.class.getSimpleName();
    private static ServiceManager sInstance;
    private final MediaItemManager mItemManager;
    private final MediaGroupManager mGroupManager;
    private Disposable mMetadataAction;
    private Disposable mUploadsAction;

    public ServiceManager() {
        MediaService service = YouTubeMediaService.instance();
        mItemManager = service.getMediaItemManager();
        mGroupManager = service.getMediaGroupManager();
    }

    public static ServiceManager instance() {
        if (sInstance == null) {
            sInstance = new ServiceManager();
        }

        return sInstance;
    }

    public void loadMetadata(Video item, OnMetadata onMetadata) {
        if (item == null) {
            return;
        }

        RxUtils.disposeActions(mMetadataAction);

        Observable<MediaItemMetadata> observable;

        if (item.mediaItem != null) {
            // Use additional data like playlist id
            observable = mItemManager.getMetadataObserve(item.mediaItem);
        } else {
            // Simply load
            observable = mItemManager.getMetadataObserve(item.videoId);
        }

        mMetadataAction = observable
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onMetadata::onMetadata,
                        error -> Log.e(TAG, "loadMetadata error: " + error));
    }

    public interface OnMetadata {
        void onMetadata(MediaItemMetadata metadata);
    }

    public void loadChannelUploads(Video item, OnMediaGroup onMediaGroup) {
        if (item == null) {
            return;
        }

        RxUtils.disposeActions(mUploadsAction);

        Observable<MediaGroup> observable = mGroupManager.getGroupObserve(item.mediaItem);

        mUploadsAction = observable
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onMediaGroup::onMediaGroup,
                        error -> Log.e(TAG, "loadChannelUploads error: " + error));
    }

    public interface OnMediaGroup {
        void onMediaGroup(MediaGroup group);
    }
}
