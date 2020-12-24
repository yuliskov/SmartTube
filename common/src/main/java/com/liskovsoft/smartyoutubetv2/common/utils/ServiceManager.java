package com.liskovsoft.smartyoutubetv2.common.utils;

import com.liskovsoft.appupdatechecker2.other.SettingsManager;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
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
    private Disposable mMetadataAction;

    public ServiceManager() {
        MediaService service = YouTubeMediaService.instance();
        mItemManager = service.getMediaItemManager();
    }

    public static ServiceManager instance() {
        if (sInstance == null) {
            sInstance = new ServiceManager();
        }

        return sInstance;
    }

    public void loadMetadata(Video video, OnMetadata onMetadata) {
        if (video == null) {
            return;
        }

        RxUtils.disposeActions(mMetadataAction);

        Observable<MediaItemMetadata> observable;

        if (video.mediaItem != null) {
            // Use additional data like playlist id
            observable = mItemManager.getMetadataObserve(video.mediaItem);
        } else {
            // Simply load
            observable = mItemManager.getMetadataObserve(video.videoId);
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
}
