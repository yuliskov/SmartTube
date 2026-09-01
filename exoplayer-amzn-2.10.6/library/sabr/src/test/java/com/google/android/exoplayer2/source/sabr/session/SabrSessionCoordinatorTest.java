package com.google.android.exoplayer2.source.sabr.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.android.exoplayer2.source.sabr.protos.videostreaming.NextRequestPolicy;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.PlaybackCookie;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.SabrContextSendingPolicy;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamProtectionStatus;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamerContext;

import org.junit.Test;

public class SabrSessionCoordinatorTest {
    @Test
    public void audioAndVideoUseOneMonotonicRequestSequence() throws Exception {
        SabrSessionCoordinator coordinator = coordinator();

        SabrSessionCoordinator.RequestTicket audio = coordinator.beginRequest();
        assertTrue(coordinator.completeRequest(audio));
        SabrSessionCoordinator.RequestTicket video = coordinator.beginRequest();

        assertEquals(0, audio.requestNumber);
        assertEquals(1, video.requestNumber);
        assertEquals(audio.generation, video.generation);
    }

    @Test
    public void requestStateRemainsRequestingUntilAllTrackRequestsComplete() throws Exception {
        SabrSessionCoordinator coordinator = coordinator();
        SabrSessionCoordinator.RequestTicket audio = coordinator.beginRequest();
        SabrSessionCoordinator.RequestTicket video = coordinator.beginRequest();

        assertTrue(coordinator.completeRequest(audio));
        assertEquals(SabrSessionCoordinator.State.REQUESTING, coordinator.snapshot().state);
        assertTrue(coordinator.completeRequest(video));
        assertEquals(SabrSessionCoordinator.State.READY, coordinator.snapshot().state);
    }

    @Test
    public void responseCookieIsPresentInFollowingStreamerContext() {
        SabrSessionCoordinator coordinator = coordinator();
        coordinator.processNextRequestPolicy(NextRequestPolicy.newBuilder()
                .setPlaybackCookie(PlaybackCookie.newBuilder().setResolution(999999).build())
                .build());

        StreamerContext context = coordinator.createStreamerContext(
                StreamerContext.ClientInfo.newBuilder().build());

        assertTrue(context.hasPlaybackCookie());
        assertTrue(context.getPlaybackCookie().size() > 0);
    }

    @Test
    public void startWithoutValueIsReportedAsUnsent() {
        SabrSessionCoordinator coordinator = coordinator();
        coordinator.processContextSendingPolicy(SabrContextSendingPolicy.newBuilder()
                .addStartPolicy(77).build());

        StreamerContext context = coordinator.createStreamerContext(
                StreamerContext.ClientInfo.newBuilder().build());

        assertEquals(1, context.getUnsentSabrContextsCount());
        assertEquals(77, context.getUnsentSabrContexts(0));
    }

    @Test
    public void redirectIsAtomicForAllFutureRequests() throws Exception {
        SabrSessionCoordinator coordinator = coordinator();
        coordinator.redirect("https://redirect.example.test/sabr?id=broadcast");

        assertEquals("https://redirect.example.test/sabr?id=broadcast",
                coordinator.beginRequest().getEndpoint());
        assertEquals(1, coordinator.snapshot().redirectCount);
    }

    @Test
    public void seekRetiresInFlightGeneration() throws Exception {
        SabrSessionCoordinator coordinator = coordinator();
        SabrSessionCoordinator.RequestTicket old = coordinator.beginRequest();

        coordinator.seek(5_000);

        assertFalse(coordinator.completeRequest(old));
        assertTrue(coordinator.beginRequest().generation > old.generation);
    }

    @Test
    public void protectionRefreshIsBoundedToOneAttempt() throws Exception {
        SabrSessionCoordinator coordinator = coordinator();
        int[] calls = {0};
        StreamProtectionStatus required = StreamProtectionStatus.newBuilder()
                .setStatus(StreamProtectionStatus.Status.ATTESTATION_REQUIRED).build();

        coordinator.processProtectionStatus(required, () -> {
            calls[0]++;
            return "c2FuaXRpemVk";
        });
        assertEquals(1, calls[0]);

        assertCategory(SabrSessionException.Category.PROTECTION,
                () -> coordinator.processProtectionStatus(required, () -> {
                    calls[0]++;
                    return "c2FuaXRpemVk";
                }));
        assertEquals(1, calls[0]);
    }

    @Test
    public void reloadLoopIsBoundedAndCloseCancelsRequests() throws Exception {
        SabrSessionCoordinator coordinator = coordinator();
        coordinator.markReloadRequired();
        coordinator.finishReload(
                "https://media.example.test/sabr?id=broadcast", null, "video-id");
        assertCategory(SabrSessionException.Category.RELOAD_FAILED,
                coordinator::markReloadRequired);

        SabrSessionCoordinator closed = coordinator();
        closed.close();
        assertCategory(SabrSessionException.Category.CANCELLED, closed::beginRequest);
    }

    private static SabrSessionCoordinator coordinator() {
        return new SabrSessionCoordinator(
                "https://media.example.test/sabr?id=broadcast",
                null,
                "video-id",
                false,
                () -> 1000);
    }

    private static void assertCategory(
            SabrSessionException.Category expected, ThrowingRunnable runnable) throws Exception {
        try {
            runnable.run();
            fail("Expected " + expected);
        } catch (SabrSessionException error) {
            assertEquals(expected, error.getCategory());
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
