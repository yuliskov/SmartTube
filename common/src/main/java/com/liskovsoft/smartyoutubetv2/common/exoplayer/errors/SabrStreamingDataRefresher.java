package com.liskovsoft.smartyoutubetv2.common.exoplayer.errors;

import com.google.android.exoplayer2.source.sabr.manifest.SabrManifest;
import com.google.android.exoplayer2.source.sabr.manifest.SabrManifestParser;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import io.reactivex.disposables.Disposable;

/**
 * Fetches a fresh player response for the playing video and hands its streaming data to the
 * running SABR manifest, so a mid-playback HTTP 403 doesn't need a full video reload.
 * If anything goes wrong, the regular error recovery (ErrorFixerController) still applies.
 */
public class SabrStreamingDataRefresher implements SabrManifest.StreamingDataRefresher {
    private static final String TAG = SabrStreamingDataRefresher.class.getSimpleName();
    private Disposable mRefreshAction;

    @Override
    public void refreshStreamingData(SabrManifest manifest) {
        String videoId = manifest.getVideoId();

        if (videoId == null) {
            manifest.onStreamingDataRefreshFailed();
            return;
        }

        RxHelper.disposeActions(mRefreshAction);

        Log.d(TAG, "Refreshing streaming data for video %s", videoId);

        // Drop the cached player response so the request below fetches a new one
        YouTubeServiceManager.instance().reloadPlayerResponse(videoId, null);

        mRefreshAction = YouTubeServiceManager.instance().getMediaItemService().getFormatInfoObserve(videoId)
                .subscribe(
                        formatInfo -> applyFormatInfo(manifest, formatInfo),
                        error -> {
                            Log.e(TAG, "Streaming data refresh failed: %s", error.getMessage());
                            manifest.onStreamingDataRefreshFailed();
                        },
                        () -> manifest.onStreamingDataRefreshFailed() // no-op if already applied
                );
    }

    private static void applyFormatInfo(SabrManifest manifest, MediaItemFormatInfo formatInfo) {
        if (formatInfo == null || formatInfo.getServerAbrStreamingUrl() == null) {
            Log.e(TAG, "Streaming data refresh: no SABR data in the new player response");
            manifest.onStreamingDataRefreshFailed();
            return;
        }

        SabrManifest fresh;

        try {
            fresh = new SabrManifestParser().parse(formatInfo);
        } catch (RuntimeException e) {
            Log.e(TAG, "Streaming data refresh: can't parse the new player response: %s", e.getMessage());
            manifest.onStreamingDataRefreshFailed();
            return;
        }

        if (manifest.updateStreamingData(fresh)) {
            Log.d(TAG, "Streaming data refreshed");
        } else {
            Log.e(TAG, "Streaming data refresh: the new player response doesn't match the playing stream");
        }
    }
}
