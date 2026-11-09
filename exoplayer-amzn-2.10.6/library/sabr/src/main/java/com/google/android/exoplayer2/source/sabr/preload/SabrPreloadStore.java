package com.google.android.exoplayer2.source.sabr.preload;

import android.os.SystemClock;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.source.sabr.protos.misc.FormatId;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SABR responses recorded ahead of time for the first requests of upcoming videos (e.g. the next Shorts).<br/>
 * Each response is served once, in the order the player asks for it.
 */
public final class SabrPreloadStore {
    /**
     * Next couple of Shorts. The first video segment takes ~1-2 MB.
     */
    private static final int MAX_VIDEOS = 3;
    /**
     * Keep the playback cookie fresh
     */
    private static final long MAX_AGE_MS = 10 * 60 * 1_000;
    private static final Map<String, Recording> sRecordings = new LinkedHashMap<>();
    private static final int MAX_PLAYER_SESSIONS = 20;
    /**
     * Streams the player has already started a server session for (from the network).
     * Their recordings must never be used: two sessions of the same stream may be cut by the server
     * after the first minute (ATTESTATION_PENDING).
     */
    private static final Map<String, Boolean> sPlayerSessions = new LinkedHashMap<String, Boolean>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > MAX_PLAYER_SESSIONS;
        }
    };

    static final class Response {
        /**
         * Full id. Same itag may have multiple variants (e.g. audio with different xtags).
         */
        final FormatId formatId;
        final long playerTimeMs;
        final byte[] data;

        Response(FormatId formatId, long playerTimeMs, byte[] data) {
            this.formatId = formatId;
            this.playerTimeMs = playerTimeMs;
            this.data = data;
        }
    }

    private static final class Recording {
        final long createdMs = SystemClock.elapsedRealtime();
        final Map<Integer, ArrayDeque<Response>> responses = new HashMap<>();

        boolean isExpired() {
            return SystemClock.elapsedRealtime() - createdMs > MAX_AGE_MS;
        }

        boolean isEmpty() {
            for (ArrayDeque<Response> queue : responses.values()) {
                if (!queue.isEmpty()) {
                    return false;
                }
            }

            return true;
        }
    }

    private SabrPreloadStore() {
    }

    public static synchronized boolean contains(String requestUrl) {
        Recording recording = sRecordings.get(toKey(requestUrl));
        return recording != null && !recording.isExpired() && !recording.isEmpty();
    }

    /**
     * The player streams this video from the network already. Don't preload it.
     */
    public static synchronized boolean isPlayerSession(String requestUrl) {
        return sPlayerSessions.containsKey(toKey(requestUrl));
    }

    /**
     * The player sends a request that isn't served from a recording. From now on the stream belongs to the player.
     */
    static synchronized void onPlayerSession(String requestUrl) {
        String key = toKey(requestUrl);
        sRecordings.remove(key);
        sPlayerSessions.put(key, true);
    }

    /**
     * Save all the responses of one video at once (partially recorded sessions are useless)
     */
    static synchronized void put(String requestUrl, Map<Integer, ArrayDeque<Response>> responses) {
        removeExpired();

        String key = toKey(requestUrl);

        if (sPlayerSessions.containsKey(key)) {
            return; // too late, the player has started its own session
        }

        Recording recording = new Recording();
        recording.responses.putAll(responses);
        sRecordings.remove(key);
        sRecordings.put(key, recording);

        while (sRecordings.size() > MAX_VIDEOS) {
            Iterator<String> iterator = sRecordings.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    /**
     * Take the next recorded response of the track if it's exactly the one the player is asking for.<br/>
     * Otherwise the whole recording is dropped and the stream continues from the network:
     * a recording must never be mixed into a session that went another way.
     */
    @Nullable
    static synchronized Response poll(String requestUrl, int trackType, FormatId formatId, long playerTimeMs) {
        String key = toKey(requestUrl);
        Recording recording = sRecordings.get(key);

        if (recording == null || recording.isExpired()) {
            onPlayerSession(requestUrl);
            return null;
        }

        ArrayDeque<Response> queue = recording.responses.get(trackType);
        Response next = queue != null ? queue.peek() : null;

        if (next == null || !next.formatId.equals(formatId) || next.playerTimeMs != playerTimeMs) {
            onPlayerSession(requestUrl);
            return null;
        }

        queue.poll();

        if (recording.isEmpty()) {
            onPlayerSession(requestUrl); // the rest of the session comes from the network
        }

        return next;
    }

    private static void removeExpired() {
        Iterator<Recording> iterator = sRecordings.values().iterator();

        while (iterator.hasNext()) {
            if (iterator.next().isExpired()) {
                iterator.remove();
            }
        }
    }

    /**
     * The request number is appended to the end of the url (see SabrManifest#getRequestUrl)
     */
    static String toKey(String requestUrl) {
        return requestUrl != null ? requestUrl.replaceFirst("[?&]rn=\\d+$", "") : null;
    }
}
