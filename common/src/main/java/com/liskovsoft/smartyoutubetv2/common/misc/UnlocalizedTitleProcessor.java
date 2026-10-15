package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.util.Pair;

import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.UnlocalizedTitleData;
import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataChangeBase.OnDataChange;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

public class UnlocalizedTitleProcessor implements OnDataChange, BrowseProcessor {
    private static final String TAG = UnlocalizedTitleProcessor.class.getSimpleName();
    private static final int CONCURRENT_REQUESTS = 5;
    private final OnItemReady mOnItemReady;
    private final MediaItemService mItemService;
    private final MainUIData mMainUIData;
    private final UnlocalizedTitleData mTitleCache;
    private boolean mIsUnlocalizedTitlesEnabled;
    private Disposable mResult;

    public UnlocalizedTitleProcessor(Context context, OnItemReady onItemReady) {
        mOnItemReady = onItemReady;
        ServiceManager service = YouTubeServiceManager.instance();
        mItemService = service.getMediaItemService();
        mMainUIData = MainUIData.instance(context);
        mTitleCache = UnlocalizedTitleData.instance(context);
        mMainUIData.setOnChange(this);
        initData();
    }

    @Override
    public void onDataChange() {
        initData();
    }

    private void initData() {
        mIsUnlocalizedTitlesEnabled = mMainUIData.isUnlocalizedTitlesEnabled();
    }

    @Override
    public void process(VideoGroup videoGroup) {
        if (!mIsUnlocalizedTitlesEnabled || videoGroup == null || videoGroup.isEmpty()) {
            return;
        }

        List<String> videoIds = new ArrayList<>();

        for (Video video : videoGroup.getVideos()) {
            if (video.deArrowProcessed) {
                continue;
            }
            video.deArrowProcessed = true;

            // Check cache first — apply immediately if hit
            String cachedTitle = mTitleCache.getTitle(video.videoId);
            if (cachedTitle != null) {
                video.deArrowTitle = cachedTitle;
                if (!Helpers.equals(video.title, video.deArrowTitle)) {
                    mOnItemReady.onItemReady(video);
                }
            } else {
                videoIds.add(video.videoId);
            }
        }

        // Only fetch titles we don't have cached
        if (videoIds.isEmpty()) {
            return;
        }

        mResult = Observable.fromIterable(videoIds)
                .flatMap(videoId -> mItemService.getUnlocalizedTitleObserve(videoId)
                        .map(newTitle -> new Pair<>(videoId, newTitle)), CONCURRENT_REQUESTS)
                .subscribe(title -> {
                    // Store in cache
                    mTitleCache.putTitle(title.first, title.second);

                    Video video = videoGroup.findVideoById(title.first);
                    if (video == null) {
                        return;
                    }
                    video.deArrowTitle = title.second;
                    if (!Helpers.equals(video.title, video.deArrowTitle)) {
                        mOnItemReady.onItemReady(video);
                    }
                },
                        error -> {
                            Log.d(TAG, "Unlocalized title: Cannot process the video");
                        });
    }

    @Override
    public void dispose() {
        RxHelper.disposeActions(mResult);
    }
}
