package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.DeArrowData;
import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataChangeBase.OnDataChange;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.disposables.Disposable;

public class DeArrowProcessor implements OnDataChange, BrowseProcessor {
    private static final String TAG = DeArrowProcessor.class.getSimpleName();
    private final OnItemReady mOnItemReady;
    private final MediaItemService mItemService;
    private final DeArrowData mDeArrowData;
    private boolean mIsReplaceTitlesEnabled;
    private boolean mIsReplaceThumbnailsEnabled;
    private Disposable mResult;

    public DeArrowProcessor(Context context, OnItemReady onItemReady) {
        mOnItemReady = onItemReady;
        ServiceManager service = YouTubeServiceManager.instance();
        mItemService = service.getMediaItemService();
        mDeArrowData = DeArrowData.instance(context);
        mDeArrowData.setOnChange(this);
        initData();
    }

    @Override
    public void onDataChange() {
        initData();
    }

    private void initData() {
        mIsReplaceTitlesEnabled = mDeArrowData.isReplaceTitlesEnabled();
        mIsReplaceThumbnailsEnabled = mDeArrowData.isReplaceThumbnailsEnabled();
    }

    @Override
    public void process(VideoGroup videoGroup) {
        if ((!mIsReplaceTitlesEnabled && !mIsReplaceThumbnailsEnabled) || videoGroup == null || videoGroup.isEmpty()) {
            return;
        }

        List<String> videoIds = getVideoIds(videoGroup);
        mResult = mItemService.getDeArrowDataObserve(videoIds)
                .subscribe(deArrowData -> {
                    Video video = videoGroup.findVideoById(deArrowData.getVideoId());
                    if (mIsReplaceTitlesEnabled) {
                        video.deArrowTitle = deArrowData.getTitle();
                    }
                    if (mIsReplaceThumbnailsEnabled) {
                        video.altCardImageUrl = deArrowData.getThumbnailUrl();
                    }
                    mOnItemReady.onItemReady(video);
                },
                error -> {
                    Log.d(TAG, "DeArrow cannot process the video");
                });
    }

    @Override
    public void dispose() {
        RxHelper.disposeActions(mResult);
    }

    private List<String> getVideoIds(VideoGroup videoGroup) {
        List<String> result = new ArrayList<>();

        for (Video video : videoGroup.getVideos()) {
            if (video.deArrowProcessed) {
                continue;
            }
            video.deArrowProcessed = true;
            result.add(video.videoId);
        }

        return result;
    }
}
