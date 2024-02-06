package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.HubService;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.DeArrowData;
import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataChangeBase.OnDataChange;
import com.liskovsoft.youtubeapi.service.YouTubeHubService;

import java.util.List;

import io.reactivex.disposables.Disposable;

public class DeArrowProcessor implements OnDataChange {
    private static final String TAG = DeArrowProcessor.class.getSimpleName();
    private final OnItemReady mOnItemReady;
    private final MediaItemService mItemService;
    private final DeArrowData mDeArrowData;
    private boolean mIsEnabled;

    public interface OnItemReady {
        void onItemReady(Video video);
    }

    public DeArrowProcessor(Context context, OnItemReady onItemReady) {
        mOnItemReady = onItemReady;
        HubService hubService = YouTubeHubService.instance();
        mItemService = hubService.getMediaItemService();
        mDeArrowData = DeArrowData.instance(context);
        mIsEnabled = mDeArrowData.isReplaceTitlesEnabled();
        mDeArrowData.setOnChange(this);
    }

    @Override
    public void onDataChange() {
        mIsEnabled = mDeArrowData.isReplaceTitlesEnabled();
    }

    public void process(VideoGroup videoGroup) {
        if (!mIsEnabled || videoGroup == null || videoGroup.isEmpty()) {
            return;
        }

        List<String> videoIds = videoGroup.getVideoIds();
        Disposable result = mItemService.getDeArrowDataObserve(videoIds)
                .subscribe(deArrowData -> {
                    Video video = videoGroup.findVideoById(deArrowData.getVideoId());
                    video.altTitle = deArrowData.getTitle();
                    mOnItemReady.onItemReady(video);
                },
                error -> {
                    Log.d(TAG, "DeArrow not working");
                });
    }
}
