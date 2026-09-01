package com.liskovsoft.smartyoutubetv2.common.app.models.playback.live;

/** Pure candidate-verification policy applied to fresh /player response facts. */
final class LiveCandidateVerifier {
    enum Status { LIVE, UPCOMING, RESTRICTED, NOT_LIVE, MISMATCH, EMPTY_STREAMING_DATA }

    static final class Facts {
        final String requestedVideoId;
        final String responseVideoId;
        final String expectedChannelId;
        final String responseChannelId;
        final boolean advertisedUpcoming;
        final boolean live;
        final boolean liveContent;
        final boolean unplayable;
        final boolean usableSource;
        final long startTimeMs;
        final String reason;

        Facts(String requestedVideoId, String responseVideoId, String expectedChannelId,
              String responseChannelId, boolean advertisedUpcoming, boolean live,
              boolean liveContent, boolean unplayable, boolean usableSource,
              long startTimeMs, String reason) {
            this.requestedVideoId = requestedVideoId;
            this.responseVideoId = responseVideoId;
            this.expectedChannelId = expectedChannelId;
            this.responseChannelId = responseChannelId;
            this.advertisedUpcoming = advertisedUpcoming;
            this.live = live;
            this.liveContent = liveContent;
            this.unplayable = unplayable;
            this.usableSource = usableSource;
            this.startTimeMs = startTimeMs;
            this.reason = reason;
        }
    }

    static final class Verification {
        final Status status;
        final String reason;

        Verification(Status status, String reason) {
            this.status = status;
            this.reason = reason;
        }
    }

    Verification verify(Facts facts, long nowMs) {
        if (facts == null || isEmpty(facts.requestedVideoId)
                || !facts.requestedVideoId.equals(facts.responseVideoId)) {
            return new Verification(Status.MISMATCH, "Player response video ID mismatch");
        }
        if (!isEmpty(facts.expectedChannelId)
                && !facts.expectedChannelId.equals(facts.responseChannelId)) {
            return new Verification(Status.MISMATCH, "Player response channel ID mismatch");
        }
        if (facts.live && !facts.unplayable) {
            return facts.usableSource
                    ? new Verification(Status.LIVE, null)
                    : new Verification(Status.EMPTY_STREAMING_DATA,
                    "Live player response has no usable source");
        }
        boolean futureStart = facts.startTimeMs > nowMs;
        if (facts.advertisedUpcoming && (facts.liveContent || futureStart)) {
            return new Verification(Status.UPCOMING, null);
        }
        if (facts.unplayable) {
            return new Verification(Status.RESTRICTED,
                    !isEmpty(facts.reason) ? facts.reason : "Candidate is unavailable in this context");
        }
        return new Verification(Status.NOT_LIVE, "Candidate is not currently live");
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }
}
