package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * YouTube may need some time to return the like status of a video the user just rated.
 * Remember recent ratings and apply them when the video is opened again.
 */
public final class RecentRatings {
    private static final long MAX_AGE_MS = 10 * 60 * 1_000;
    private static final Map<String, long[]> sRatings = new HashMap<>(); // videoId -> {likeStatus, timeMs}

    private RecentRatings() {
    }

    public static synchronized void record(String videoId, int likeStatus) {
        if (videoId == null) {
            return;
        }

        cleanup();
        sRatings.put(videoId, new long[] {likeStatus, System.currentTimeMillis()});
    }

    /**
     * @return the recent rating of the video or the received one if there's none
     */
    public static synchronized int getLikeStatus(String videoId, int received) {
        long[] rating = videoId != null ? sRatings.get(videoId) : null;

        if (rating == null || System.currentTimeMillis() - rating[1] > MAX_AGE_MS) {
            return received;
        }

        return (int) rating[0];
    }

    private static void cleanup() {
        long now = System.currentTimeMillis();
        Iterator<long[]> iterator = sRatings.values().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next()[1] > MAX_AGE_MS) {
                iterator.remove();
            }
        }
    }
}
