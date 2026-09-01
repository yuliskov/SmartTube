package com.liskovsoft.smartyoutubetv2.common.app.models.playback.live;

/** Process-local, metadata-only playback status consumed by the existing TV debug overlay. */
public final class LivePlaybackStatus {
    public static final class Snapshot {
        public final String redactedVideoId;
        public final LivePlaybackSession.State state;
        public final LivePlaybackSourceSelector.Source source;
        public final String fallbackReason;
        public final long generation;

        private Snapshot(String redactedVideoId, LivePlaybackSession.State state,
                         LivePlaybackSourceSelector.Source source, String fallbackReason,
                         long generation) {
            this.redactedVideoId = redactedVideoId;
            this.state = state;
            this.source = source;
            this.fallbackReason = fallbackReason;
            this.generation = generation;
        }
    }

    private static Snapshot current = new Snapshot(null, LivePlaybackSession.State.IDLE,
            null, null, 0);

    private LivePlaybackStatus() {}

    public static synchronized void update(String videoId, LivePlaybackSession session) {
        String fallback = null;
        if (!session.getFallbackReasons().isEmpty()) {
            fallback = session.getFallbackReasons().get(session.getFallbackReasons().size() - 1);
        }
        current = new Snapshot(redact(videoId), session.getState(), session.getCurrentSource(),
                fallback, session.getGeneration());
    }

    public static synchronized void clear() {
        current = new Snapshot(null, LivePlaybackSession.State.STOPPED, null, null,
                current.generation + 1);
    }

    public static synchronized Snapshot snapshot() {
        return current;
    }

    private static String redact(String videoId) {
        if (videoId == null || videoId.length() < 4) return null;
        return "…" + videoId.substring(videoId.length() - 4);
    }
}
