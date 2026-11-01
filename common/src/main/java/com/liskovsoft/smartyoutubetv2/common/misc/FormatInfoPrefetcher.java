package com.liskovsoft.smartyoutubetv2.common.misc;

import android.os.SystemClock;

import androidx.annotation.Nullable;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

/**
 * Loads the format info of a video before it's opened (e.g. while its tile has the focus),
 * so playback starts without waiting for it.<br/>
 * One video at a time, single use, short lived.
 */
public final class FormatInfoPrefetcher {
    private static final long MAX_AGE_MS = 120_000;
    private static FormatInfoPrefetcher sInstance;
    private String mVideoId;
    private Observable<MediaItemFormatInfo> mPending;
    private Disposable mAction;
    private MediaItemFormatInfo mFormatInfo;
    private long mLoadedAt;

    private FormatInfoPrefetcher() {
    }

    public static FormatInfoPrefetcher instance() {
        if (sInstance == null) {
            sInstance = new FormatInfoPrefetcher();
        }

        return sInstance;
    }

    public void prefetch(@Nullable String videoId) {
        if (videoId == null || (videoId.equals(mVideoId) && (isLoading() || isFresh()))) {
            return;
        }

        cancel();

        mVideoId = videoId;
        mPending = YouTubeServiceManager.instance().getMediaItemService().getFormatInfoObserve(videoId).cache();
        mAction = mPending.subscribe(
                formatInfo -> {
                    if (videoId.equals(mVideoId)) {
                        mFormatInfo = formatInfo;
                        mLoadedAt = SystemClock.uptimeMillis();
                    }
                },
                error -> {
                    if (videoId.equals(mVideoId)) {
                        cancel();
                    }
                }
        );
    }

    /**
     * The prefetched (or still loading) format info of the video, null if there's none.<br/>
     * Another prefetch is cancelled so it doesn't hold the engine.
     */
    @Nullable
    public Observable<MediaItemFormatInfo> take(@Nullable String videoId) {
        Observable<MediaItemFormatInfo> result = null;

        if (videoId != null && videoId.equals(mVideoId)) {
            if (isFresh()) {
                result = Observable.just(mFormatInfo);
            } else if (isLoading()) {
                result = mPending;
                mAction = null; // the caller takes over, don't dispose the shared request
            }
        }

        cancel();

        return result;
    }

    public void cancel() {
        RxHelper.disposeActions(mAction);
        mAction = null;
        mPending = null;
        mVideoId = null;
        mFormatInfo = null;
        mLoadedAt = 0;
    }

    private boolean isLoading() {
        return mPending != null && mFormatInfo == null && RxHelper.isAnyActionRunning(mAction);
    }

    private boolean isFresh() {
        return mFormatInfo != null && SystemClock.uptimeMillis() - mLoadedAt < MAX_AGE_MS;
    }
}
