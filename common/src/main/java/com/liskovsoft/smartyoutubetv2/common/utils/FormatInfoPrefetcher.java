package com.liskovsoft.smartyoutubetv2.common.utils;

import android.os.Handler;
import android.os.Looper;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import io.reactivex.disposables.Disposable;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

/**
 * Loads the playback info of the focused video in the background, so the click starts the player without waiting.
 * The media service keeps the last loaded info in its own cache, the real load just reuses it (and waits for it if it's still running).
 */
public final class FormatInfoPrefetcher {
    private static final String TAG = FormatInfoPrefetcher.class.getSimpleName();
    private static final long DELAY_MS = 600;   // don't prefetch while the user is quickly scrolling
    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static Disposable sAction;
    private static String sLastVideoId;

    private FormatInfoPrefetcher() {
    }

    public static void onVideoFocused(Video video) {
        sHandler.removeCallbacksAndMessages(null);

        if (video == null || video.videoId == null || !video.hasVideo() || video.isLive || video.isUpcoming || video.isShorts
                || video.videoId.equals(sLastVideoId)) {
            return;
        }

        String videoId = video.videoId;
        sHandler.postDelayed(() -> prefetch(videoId), DELAY_MS);
    }

    private static void prefetch(String videoId) {
        RxHelper.disposeActions(sAction);
        sLastVideoId = videoId;

        try {
            MediaItemService service = YouTubeServiceManager.instance().getMediaItemService();
            sAction = RxHelper.execute(service.getFormatInfoObserve(videoId), info -> {}, error -> Log.e(TAG, "Prefetch failed: %s", error.getMessage()));
        } catch (Throwable e) {
            Log.e(TAG, "Prefetch failed: %s", e.getMessage());
        }
    }
}
