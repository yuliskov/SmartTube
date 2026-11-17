package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The playlist info from YouTube may need some time (10+ seconds) to reflect a video that was just added to or removed
 * from a playlist. Remember the user's recent edits and apply them on top of the received info.
 */
public final class RecentPlaylistEdits {
    private static final long MAX_AGE_MS = 5 * 60 * 1_000;
    private static final Map<String, Edit> sEdits = new HashMap<>();

    private static final class Edit {
        final boolean added;
        final long timeMs;

        Edit(boolean added, long timeMs) {
            this.added = added;
            this.timeMs = timeMs;
        }
    }

    private RecentPlaylistEdits() {
    }

    public static synchronized void record(String videoId, String playlistId, boolean added) {
        if (videoId == null || playlistId == null) {
            return;
        }

        cleanup();
        sEdits.put(key(videoId, playlistId), new Edit(added, System.currentTimeMillis()));
    }

    /**
     * @return true/false if the video was recently added to/removed from the playlist, null if there's no recent edit
     */
    @Nullable
    public static synchronized Boolean get(String videoId, String playlistId) {
        if (videoId == null || playlistId == null) {
            return null;
        }

        Edit edit = sEdits.get(key(videoId, playlistId));

        if (edit == null || System.currentTimeMillis() - edit.timeMs > MAX_AGE_MS) {
            return null;
        }

        return edit.added;
    }

    /**
     * The received selection state with the recent edit applied.
     */
    public static boolean isSelected(String videoId, String playlistId, boolean received) {
        Boolean recent = get(videoId, playlistId);
        return recent != null ? recent : received;
    }

    private static String key(String videoId, String playlistId) {
        return videoId + "|" + playlistId;
    }

    private static void cleanup() {
        long now = System.currentTimeMillis();
        Iterator<Edit> iterator = sEdits.values().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().timeMs > MAX_AGE_MS) {
                iterator.remove();
            }
        }
    }
}
