package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.content.Context;
import androidx.annotation.Nullable;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background queue that fetches the start of the upcoming Shorts, so they start instantly.<br/>
 * The media is fetched the way the player will open the video:
 * {@link ShortsMediaCache} (dash, disk cache) or {@link ShortsSabrPreloader} (sabr, recorded responses).<br/>
 * The videos are processed one by one in the order they were added (the nearest one first, it gets the whole bandwidth).
 * A video that is still wanted is never interrupted or started twice, see {@link #retainOnly}.
 */
public final class ShortsPreloader {
    private static final String TAG = ShortsPreloader.class.getSimpleName();
    public static final int TYPE_DASH = 0;
    public static final int TYPE_SABR = 1;
    private static final int IDLE_THREAD_TIMEOUT_SEC = 30;
    private static final ThreadPoolExecutor sExecutor = createExecutor();
    /**
     * Videos that are queued or running, with their cancel flags
     */
    private static final Map<String, AtomicBoolean> sPending = new HashMap<>();

    private ShortsPreloader() {
    }

    /**
     * @param type how the player will open the video: {@link #TYPE_DASH} or {@link #TYPE_SABR}
     * @param videoItag itag of the video track that is playing now (the player will most likely pick the same quality)
     * @param audioItag itag of the audio track that is playing now
     */
    public static void preload(Context context, MediaItemFormatInfo formatInfo, int type, @Nullable String videoItag, @Nullable String audioItag) {
        String videoId = formatInfo != null ? formatInfo.getVideoId() : null;

        if (context == null || videoId == null) {
            return;
        }

        Context appContext = context.getApplicationContext();
        AtomicBoolean canceled = new AtomicBoolean();

        synchronized (sPending) {
            if (sPending.containsKey(videoId)) {
                return; // queued or running already
            }

            sPending.put(videoId, canceled);
        }

        try {
            sExecutor.execute(() -> {
                try {
                    if (canceled.get()) {
                        return;
                    }

                    if (type == TYPE_SABR) {
                        ShortsSabrPreloader.preload(appContext, formatInfo, videoItag, audioItag, canceled);
                    } else {
                        ShortsMediaCache.prefetch(appContext, formatInfo, videoItag, audioItag, canceled);
                    }
                } catch (RuntimeException e) { // The preload is optional. It should never break anything.
                    Log.e(TAG, "Preload of %s failed: %s", videoId, e.getMessage());
                } finally {
                    onFinished(videoId, canceled);
                }
            });
        } catch (RuntimeException e) { // e.g. RejectedExecutionException
            onFinished(videoId, canceled);
            Log.e(TAG, "Can't start the preload of %s: %s", videoId, e.getMessage());
        }
    }

    /**
     * Cancel the videos that aren't wanted anymore (the user has skipped them).
     * The ones that are still wanted continue untouched.
     * @param videoIds videos to keep (null or empty: cancel everything)
     */
    public static void retainOnly(@Nullable Collection<String> videoIds) {
        synchronized (sPending) {
            Iterator<Map.Entry<String, AtomicBoolean>> iterator = sPending.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<String, AtomicBoolean> entry = iterator.next();

                if (videoIds == null || !videoIds.contains(entry.getKey())) {
                    entry.getValue().set(true);
                    iterator.remove();
                }
            }
        }
    }

    public static void cancelAll() {
        retainOnly(null);
    }

    private static void onFinished(String videoId, AtomicBoolean canceled) {
        synchronized (sPending) {
            if (sPending.get(videoId) == canceled) { // not replaced by a newer task
                sPending.remove(videoId);
            }
        }
    }

    /**
     * One worker thread that goes away when there's nothing to preload (e.g. the user isn't watching Shorts)
     */
    private static ThreadPoolExecutor createExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, IDLE_THREAD_TIMEOUT_SEC, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
                runnable -> {
                    Thread thread = new Thread(runnable, "ShortsPreloader");
                    thread.setDaemon(true);
                    // The video that is playing now is more important
                    thread.setPriority(Thread.NORM_PRIORITY - 1);
                    return thread;
                });
        executor.allowCoreThreadTimeOut(true);

        return executor;
    }
}
