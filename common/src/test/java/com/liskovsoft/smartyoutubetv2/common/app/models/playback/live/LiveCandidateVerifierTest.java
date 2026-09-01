package com.liskovsoft.smartyoutubetv2.common.app.models.playback.live;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LiveCandidateVerifierTest {
    private static final String VIDEO = "dQw4w9WgXcQ";
    private static final String CHANNEL = "UC1234567890123456789012";
    private final LiveCandidateVerifier verifier = new LiveCandidateVerifier();

    @Test public void acceptsMatchingLiveWithUsableSource() {
        assertEquals(LiveCandidateVerifier.Status.LIVE,
                verify(VIDEO, CHANNEL, false, true, true, false, true, 0).status);
    }

    @Test public void rejectsCandidateChannelMismatch() {
        assertEquals(LiveCandidateVerifier.Status.MISMATCH,
                verify(VIDEO, "UC9999999999999999999999", false,
                        true, true, false, true, 0).status);
    }

    @Test public void rejectsOkLookingLiveWithEmptyStreamingData() {
        assertEquals(LiveCandidateVerifier.Status.EMPTY_STREAMING_DATA,
                verify(VIDEO, CHANNEL, false, true, true, false, false, 0).status);
    }

    @Test public void preservesPlayerVerifiedUpcoming() {
        assertEquals(LiveCandidateVerifier.Status.UPCOMING,
                verify(VIDEO, CHANNEL, true, false, true, true,
                        false, 2_000).status);
    }

    @Test public void preservesRestrictedCandidate() {
        assertEquals(LiveCandidateVerifier.Status.RESTRICTED,
                verify(VIDEO, CHANNEL, false, false, false, true,
                        false, 0).status);
    }

    private LiveCandidateVerifier.Verification verify(
            String responseVideo, String responseChannel, boolean upcoming,
            boolean live, boolean liveContent, boolean unplayable,
            boolean usable, long startMs) {
        return verifier.verify(new LiveCandidateVerifier.Facts(
                VIDEO, responseVideo, CHANNEL, responseChannel, upcoming,
                live, liveContent, unplayable, usable, startMs, "restricted"), 1_000);
    }
}
